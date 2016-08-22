package com.moe365.mopi.net.channel;

import java.util.List;

public interface DataSource {
	List<DataChannel> getAvailableChannels();
	void registerChannel(DataChannel channel);
	void unregisterChannel(DataChannel channel);
}
