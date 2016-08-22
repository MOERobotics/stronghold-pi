package com.moe365.mopi.net.channel;

public enum DataChannelMediaType {
	/**
	 * The META media type is for the meta channel only (id #0). This channel
	 * contians packets pertaining to the DataStream as a whole, such as
	 * SERVER_HELLO and CHANNEL_ENUMERATION_REQUEST
	 */
	META,
	/**
	 * A channel that sends an audio stream
	 */
	AUDIO,
	/**
	 * A channel that sends a video stream.
	 */
	VIDEO,
	AUDIO_VIDEO,
	/**
	 * A channel that allows for property getting and setting
	 */
	PROPERTY_ACCESS, TEXT_STREAM, OBJECT_STREAM, FUTURE;
}