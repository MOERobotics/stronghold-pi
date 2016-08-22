package com.moe365.mopi.net.impl;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.moe365.mopi.net.channel.DataChannel;
import com.moe365.mopi.net.channel.DataSource;

public class WsDataSource implements DataSource {
	protected final AtomicInteger lastId = new AtomicInteger(0);
	protected ResponseHandlerManager responseHandlerManager = new ResponseHandlerManager();
	protected ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
		volatile int threadId = 0;

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("DataSource-" + threadId++);
			return t;
		}
	});

	@Override
	public List<DataChannel> getAvailableChannels() {
		// TODO Auto-generated method stub	
		return null;
	}

	@Override
	public void registerChannel(DataChannel channel) {
		// TODO Auto-generated method stub

	}

	@Override
	public void unregisterChannel(DataChannel channel) {
		// TODO Auto-generated method stub

	}

}
