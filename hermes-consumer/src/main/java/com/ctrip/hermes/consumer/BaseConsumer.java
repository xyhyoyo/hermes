package com.ctrip.hermes.consumer;

import java.util.List;

import com.ctrip.hermes.core.constants.CatConstants;
import com.ctrip.hermes.core.message.ConsumerMessage;
import com.dianping.cat.Cat;
import com.dianping.cat.configuration.NetworkInterfaceManager;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.spi.MessageTree;

public abstract class BaseConsumer<T> implements Consumer<T> {

	@Override
	public void consume(List<ConsumerMessage<T>> msgs) {
		if (msgs != null && !msgs.isEmpty()) {
			String topic = msgs.get(0).getTopic();

			for (ConsumerMessage<T> msg : msgs) {
				Transaction t = Cat.newTransaction("Message.Consumed", topic);
				MessageTree tree = Cat.getManager().getThreadLocalMessageTree();
				String rootMsgId = msg.getProperty(CatConstants.ROOT_MESSAGE_ID);
				String parentMsgId = msg.getProperty(CatConstants.CURRENT_MESSAGE_ID);
				String msgId = msg.getProperty(CatConstants.SERVER_MESSAGE_ID);
				tree.setRootMessageId(rootMsgId);
				tree.setParentMessageId(parentMsgId);
				tree.setMessageId(msgId);

				try {
					t.addData("topic", topic);
					t.addData("key", msg.getKey());
					t.addData("groupId", getGroupId());
					// TODO
					t.addData("appId", "demo-app");

					consume(msg);

					String ip = NetworkInterfaceManager.INSTANCE.getLocalHostAddress();
					Cat.logEvent("Consumer:" + ip, msg.getTopic() + ":" + getGroupId(), Event.SUCCESS, "key=" + msg.getKey());
					Cat.logEvent("Message:" + topic, "Consumed:" + ip, Event.SUCCESS, "key=" + msg.getKey());
					Cat.logMetricForCount(msg.getTopic());
					t.setStatus(msg.isSuccess() ? Transaction.SUCCESS : "FAILED-WILL-RETRY");
				} catch (RuntimeException | Error e) {
					Cat.logError(e);
					t.setStatus(e);
				} finally {
					t.complete();
				}
			}

		}
	}

	protected String getGroupId() {
		Subscribe s = this.getClass().getAnnotation(Subscribe.class);

		if (s != null) {
			return s.groupId();
		} else {
			return "Unknown";
		}
	}

	protected abstract void consume(ConsumerMessage<T> msg);

}
