package com.ctrip.hermes.channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.unidal.lookup.annotation.Inject;
import org.unidal.tuple.Pair;

import com.ctrip.hermes.storage.impl.StorageMessageQueue;
import com.ctrip.hermes.storage.message.Message;
import com.ctrip.hermes.storage.message.Resend;
import com.ctrip.hermes.storage.pair.AbstractPair;
import com.ctrip.hermes.storage.pair.ClusteredPair;
import com.ctrip.hermes.storage.pair.StoragePair;
import com.ctrip.hermes.storage.spi.Storage;
import com.ctrip.hermes.storage.storage.Locatable;

public class MessageQueueMonitor implements Initializable {

	@Inject
	private MessageQueueManager m_queueManager;

	public MessageQueueStatus status() throws Exception {
		LocalMessageQueueManager m = (LocalMessageQueueManager) m_queueManager;
		Map<Pair<String, String>, StorageMessageQueue> queues = m.getQueues();

		MessageQueueStatus result = new MessageQueueStatus();

		for (Map.Entry<Pair<String, String>, StorageMessageQueue> entry : queues.entrySet()) {
			String topic = entry.getKey().getKey();
			String groupId = entry.getKey().getValue();
			StorageMessageQueue q = entry.getValue();

			if ("invalid".equals(groupId)) {
				// producer message queue
				result.addTopic(topic, readTopMessages(q));
			}

			StoragePair<Message> msgPair = q.getMsgPair();
			StoragePair<Resend> resendPair = q.getResendPair();

			ConsumerStatus<Message> s1 = new ConsumerStatus<>();
			s1.setGroupId(groupId);
			s1.setTopic(topic);

			ConsumerStatus<Resend> s2 = new ConsumerStatus<>();
			s2.setGroupId(groupId);
			s2.setTopic(topic);

			inspect(msgPair, s1);
			inspect(resendPair, s2);

			result.addConsumerStatus(s1, s2);
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private List<Message> readTopMessages(StorageMessageQueue q) throws Exception {
		ClusteredPair<?> msgPair = (ClusteredPair<?>) q.getMsgPair();
		List<? extends StoragePair<?>> childPairs = msgPair.getChildPairs();

		List<Message> result = new ArrayList<Message>();
		for (StoragePair<?> p : childPairs) {
			Storage<?> main = ((AbstractPair<?>) p).getMain();
			Message top = (Message) main.top();
			if (top != null) {
				result.addAll((Collection<? extends Message>) main.createBrowser(top.getOffset().getOffset() - 10).read(10));
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private void report(MessageQueueStatus status) {
		for (Map.Entry<String, List<Message>> entry : status.getTopics().entrySet()) {
			System.out.println(String.format("+++++%s+++++", entry.getKey()));
			for (Message msg : entry.getValue()) {
				System.out.println(msg.getOffset().getOffset() + ":" + new String(((Message) msg).getContent()));
			}
			System.out.println(String.format("-----%s-----", entry.getKey()));
			System.out.println();
		}

		for (Pair<?, ?> pair : status.getConsumers()) {
			ConsumerStatus<Message> s1 = (ConsumerStatus<Message>) pair.getKey();
			ConsumerStatus<Message> s2 = (ConsumerStatus<Message>) pair.getValue();

			System.out.println(String.format("=====Topic: %s, GroupId: %s=====", s1.getTopic(), s1.getGroupId()));
			System.out.println(String.format("\tMain: Top: %s, Next: %s", s1.getTopOffset(), s1.getNextConsumeOffset()));
			System.out.println(String.format("\tResend: Top: %s, Next: %s", s2.getTopOffset(), s2.getNextConsumeOffset()));
			for (Locatable msg : s1.getNearbyMessages()) {
				System.out.println(msg.getOffset().getOffset() + ":" + new String(((Message) msg).getContent()));
			}
			for (Locatable resend : s2.getNearbyMessages()) {
				System.out.println(resend.getOffset().getOffset() + ":" + ((Resend) resend).getRange());
			}
		}
	}

	private void inspect(StoragePair<?> pair, ConsumerStatus<?> s) throws Exception {
		if (pair instanceof ClusteredPair<?>) {
			List<? extends StoragePair<?>> childPairs = ((ClusteredPair<?>) pair).getChildPairs();
			for (StoragePair<?> cp : childPairs) {
				// only one pair will be recorded
				inspect(cp, s);
			}
		} else if (pair instanceof AbstractPair<?>) {
			AbstractPair<?> ap = (AbstractPair<?>) pair;
			Locatable mainTop = ap.getMain().top();

			long curOffset = ap.getMainBrowser().currentOffset();
			s.setNextConsumeOffset(curOffset);
			s.setTopOffset(mainTop == null ? -1 : mainTop.getOffset().getOffset());

			s.setNearbyMessages(ap.getMain().createBrowser(curOffset - 5).read(10));
		} else {
			throw new RuntimeException("Unknown pair class " + pair.getClass());
		}
	}

	@Override
	public void initialize() throws InitializationException {
		new Thread() {
			public void run() {
				while (true) {
					try {
						report(status());
					} catch (Exception e) {
						e.printStackTrace();
					}
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
					}
				}
			}
		};
	}

	public static class MessageQueueStatus {
		private Map<String /* topic */, List<Message>> m_topics = new HashMap<>();

		private List<Pair<ConsumerStatus<Message>, ConsumerStatus<Resend>>> m_consumers = new ArrayList<>();

		public void addTopic(String topic, List<Message> topMessages) {
			m_topics.put(topic, topMessages);
		}

		public void addConsumerStatus(ConsumerStatus<Message> s1, ConsumerStatus<Resend> s2) {
			m_consumers.add(new Pair<ConsumerStatus<Message>, ConsumerStatus<Resend>>(s1, s2));
		}

		public Map<String, List<Message>> getTopics() {
			return m_topics;
		}

		public List<Pair<ConsumerStatus<Message>, ConsumerStatus<Resend>>> getConsumers() {
			return m_consumers;
		}

	}

	public static class ConsumerStatus<T> {
		private String m_topic;

		private String m_groupId;

		private long m_topOffset;

		private long m_nextConsumeOffset;

		private List<Locatable> m_nearbyMessages;

		public String getTopic() {
			return m_topic;
		}

		public void setTopic(String topic) {
			m_topic = topic;
		}

		public String getGroupId() {
			return m_groupId;
		}

		public void setGroupId(String groupId) {
			m_groupId = groupId;
		}

		public long getTopOffset() {
			return m_topOffset;
		}

		public void setTopOffset(long topOffset) {
			m_topOffset = topOffset;
		}

		public long getNextConsumeOffset() {
			return m_nextConsumeOffset;
		}

		public void setNextConsumeOffset(long nextConsumeOffset) {
			m_nextConsumeOffset = nextConsumeOffset;
		}

		public List<? extends Locatable> getNearbyMessages() {
			return m_nearbyMessages;
		}

		@SuppressWarnings("unchecked")
		public void setNearbyMessages(List<?> nearbyMessages) {
			m_nearbyMessages = (List<Locatable>) nearbyMessages;
		}

	}

}