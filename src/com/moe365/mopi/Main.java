package com.moe365.mopi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.moe365.mopi.CommandLineParser.ParsedCommandLineArguments;
import com.moe365.mopi.geom.Polygon;
import com.moe365.mopi.geom.Polygon.PointNode;
import com.moe365.mopi.geom.PreciseRectangle;
import com.moe365.mopi.net.MPHttpServer;
import com.moe365.mopi.processing.AbstractImageProcessor;
import com.moe365.mopi.processing.ContourTracer;
import com.moe365.mopi.processing.DebuggingDiffGenerator;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import au.edu.jcu.v4l4j.Control;
import au.edu.jcu.v4l4j.ControlList;
import au.edu.jcu.v4l4j.ImageFormat;
import au.edu.jcu.v4l4j.ImagePalette;
import au.edu.jcu.v4l4j.JPEGFrameGrabber;
import au.edu.jcu.v4l4j.V4L4JConstants;
import au.edu.jcu.v4l4j.VideoDevice;
import au.edu.jcu.v4l4j.exceptions.ControlException;
import au.edu.jcu.v4l4j.exceptions.StateException;
import au.edu.jcu.v4l4j.exceptions.UnsupportedMethod;
import au.edu.jcu.v4l4j.exceptions.V4L4JException;

/**
 * Main entry point for Moe Pi.
 * <p>
 * Command line options include:
 * <dl>
 * <dt>-?</dt>
 * <dt>-h</dt>
 * <dt>--help</dt>
 * <dd>Displays help and quits</dd>
 * <dt>-C [device]</dt>
 * <dt>--camera [device]</dt>
 * <dd>Specify which camera to use</dd>
 * <dt>-v</dt>
 * <dt>--verbose</dt>
 * <dd>Enable verbose printing</dd>
 * <dt>--out</dt>
 * <dd>Specify where to print output to. STDOUT prints to standard out, and NULL disables logging
 * (similar to printing to <code>/dev/null</code>)</dd>
 * </dl>
 * </p>
 * 
 * @author mailmindlin
 */
public class Main {
	public static final int DEFAULT_PORT = 5800;
	public static final int BOILER_TARGET_WIDTH = 20;
	public static final int BOILER_TARGET_HEIGHT = 7;
	/**
	 * Version string. Should be semantically versioned.
	 * @see <a href="semver.org">semver.org</a>
	 */
	public static final String version = "0.4.0-alpha";
	public static int width;
	public static int height;
	public static volatile boolean processorEnabled = true;
	public static VideoDevice camera;
	public static RoboRioClient rioClient;
	public static JPEGFrameGrabber frameGrabber;
	public static AbstractImageProcessor<?> processor;
	
	/**
	 * Main entry point.
	 * @param fred Command line arguments
	 * @throws IOException
	 * @throws V4L4JException
	 * @throws InterruptedException
	 */
	public static void main(String...fred) throws IOException, V4L4JException, InterruptedException {
		CommandLineParser parser = buildParser();
		ParsedCommandLineArguments parsed = parser.apply(fred);
		
		if (parsed.isFlagSet("--help")) {
			System.out.println(parser.getHelpString());
			System.exit(0);
		}
		
		if (parsed.isFlagSet("--rebuild-parser")) {
			System.out.print("Building parser...\t");
			buildParser();
			File outputFile = new File("src/resources/parser.ser");
			if (outputFile.exists())
				outputFile.delete();
			try (ObjectOutput out = new ObjectOutputStream(new FileOutputStream(outputFile))) {
				out.writeObject(parser);
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Done.");
			System.exit(0);
		}
		
		width = parsed.getOrDefault("--width", 640);
		height = parsed.getOrDefault("--height", 480);
		//System.out.println("Frame size: " + width + "x" + height);
		
		final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
			final UncaughtExceptionHandler handler = (t, e) -> {
				System.err.println("Thread " + t + " had a problem");
				e.printStackTrace();
			};
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setName("stronghold-pi-" + t.getId());
				//Print all exceptions to stderr
				t.setUncaughtExceptionHandler(handler);
				return t;
			}
			
		});
		
		
		final MPHttpServer server = initServer(parsed, executor);
		
		final VideoDevice device = camera = initCamera(parsed);
		
		final GpioPinDigitalOutput gpioPin = initGpio(parsed);
		
		final RoboRioClient client = initClient(parsed, executor);
		
		final AbstractImageProcessor<?> tracer = processor = initProcessor(parsed, server, client);
		
		
		// Run test, if required
		if (parsed.isFlagSet("--test")) {
			String target = parsed.get("--test");
			switch (target) {
				case "controls":
					testControls(device);
					break;
				case "client":
					testClient(client);
					break;
				case "processing":
					testProcessing(processor, parsed);
					break;
				default:
					System.err.println("Unknown test '" + target + "'");
			}
			device.release();
			System.exit(0);
		}
		
		final int jpegQuality = parsed.getOrDefault("--jpeg-quality", 80);
		System.out.println("JPEG quality: " + jpegQuality + "%");
		if (device != null) {
			final JPEGFrameGrabber fg = frameGrabber = device.getJPEGFrameGrabber(width, height, 0, V4L4JConstants.STANDARD_WEBCAM, jpegQuality,
					device.getDeviceInfo().getFormatList().getNativeFormatOfType(ImagePalette.YUYV));
			fg.setFrameInterval(parsed.getOrDefault("--fps-num", 1), parsed.getOrDefault("--fps-denom", 10));
			System.out.println("Framerate: " + fg.getFrameInterval());
			
			
			final AtomicBoolean ledState = new AtomicBoolean(false);
			
			final AtomicLong ledUpdateTimestamp = new AtomicLong(0);
			
			final int gpioDelay = parsed.getOrDefault("--gpio-delay", 5);
			
			fg.setCaptureCallback(frame -> {
					try {
						boolean gpioState;
						//Drop frames taken before the LED had time to flash
						if (gpioPin != null) {
							long frameTimestamp = frame.getCaptureTime();
							final long newTimestamp = System.nanoTime() / 1000 + gpioDelay;
	//						System.out.format("F: %d C: %d U: %d N: %d\n", frameTimestamp, System.nanoTime() / 1000, ledUpdateTimestamp.get(), newTimestamp);
							if (ledUpdateTimestamp.accumulateAndGet(frameTimestamp, (threshold, _frameTimestmp)->(_frameTimestmp >= threshold ? newTimestamp : threshold)) != newTimestamp) {
								//Drop frame (it was old)
	//							System.out.println("[drop frame]");
								frame.recycle();
								return;
							}
							
							//Toggle GPIO pin (change the LED's state)
							
							gpioState = !ledState.get();
							gpioPin.setState(gpioState || (!processorEnabled));
							ledState.set(gpioState);
						} else {
							gpioState = false;
						}
						
						//Offer frame to server & tracer
						if (server != null && !gpioState)
							//Only offer frames that were taken while the light was on 
							server.offerFrame(frame);
						
						if (tracer != null && processorEnabled) {
							//The tracer will call frame.recycle() when it's done with the frame
							tracer.offerFrame(frame, gpioState);
						} else {
							frame.recycle();
						}
					} catch (Exception e) {
						//Make sure to print any/all exceptions
						e.printStackTrace();
						throw e;
					}
				},
				e -> {
					e.printStackTrace();
					fg.stopCapture();
					try {
						if (server != null)
							server.shutdown();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				});
			fg.startCapture();
		}
	}
	
	protected static void testClient(RoboRioClient client) throws IOException, InterruptedException {
		System.out.println("RUNNING TEST: CLIENT");
		//just spews out UDP packets on a 3s loop
		while (true) {
			System.out.println("Writing r0");
			client.writeNoneFound();
			Thread.sleep(1000);
			System.out.println("Wrinting r1");
			client.writeOneFound(1.0, 2.0, 3.0, 4.0);
			Thread.sleep(1000);
			System.out.println("Writing r2");
			client.writeTwoFound(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.2);
			Thread.sleep(1000);
		}
	}
	
	protected static void testSSE(MJPEGServer server) throws InterruptedException {
		System.out.println("RUNNING TEST: SSE");
		while (!Thread.interrupted()) {
			List<PreciseRectangle> rects = new ArrayList<>(2);
			server.offerRectangles(rects);
			Thread.sleep(1000);
			rects.add(new PreciseRectangle(0.0,0.0,0.2,0.2));
			server.offerRectangles(rects);
			Thread.sleep(1000);
			rects.add(new PreciseRectangle(0.25,0.75,0.3,0.1));
			server.offerRectangles(rects);
			Thread.sleep(1000);
		}
	}
	
	private static long prof(ImageProcessor processor, BufferedImage[] on, BufferedImage[] off) {
		final int len = on.length;
		final int n = 50;
		long[] sums = new long[len];
		for (int i = 0; i < 50; i++) {
			for (int j = 0; j < len; j++) {
				long start = System.nanoTime();
				processor.apply(on[j], off[j]);
				long end = System.nanoTime();
				sums[j] += (end - start) / n;
			}
		}
		long result = 0;
		for (int i = 0; i < len; i++)
			result += sums[i] / len;
		return result;
	}
	
	protected static void testProcessing(AbstractImageProcessor<?> _processor, ParsedCommandLineArguments args) throws IOException, InterruptedException {
		File dir = new File(args.get("--test-images"));
		ImageProcessor processor = (ImageProcessor) _processor;
		int numImages = 0;
		List<Color> colors = Arrays.asList(Color.RED, Color.BLUE, Color.GREEN);
		while (true) {
			File onImgFile = new File(dir.getAbsolutePath(), "off" + numImages + ".png");
			File offImgFile = new File(dir.getAbsolutePath(), "on" + numImages + ".png");
			if (!(onImgFile.exists() && offImgFile.exists()))
				break;
			numImages++;
			System.out.println("========== IMAGE " + numImages + " ===========");
			BufferedImage onImg = ImageIO.read(onImgFile);
			BufferedImage offImg = ImageIO.read(offImgFile);
			List<PreciseRectangle> rectangles = processor.apply(onImg, offImg);
			BufferedImage out = ((DebuggingDiffGenerator)processor.diff).imgFlt;
			System.out.println("Found rectangles " + rectangles);

			Graphics2D g = out.createGraphics();
			int i = 0;
			for (PreciseRectangle rect : rectangles) {
				g.setColor(colors.get(i++));
				g.drawRect((int)(rect.getX() * width), (int) (rect.getY() * height), (int) (rect.getWidth() * width), (int) (rect.getHeight() * height));
			}
			g.dispose();

			File outFlt = new File("img", "flt" + numImages + ".png");
			ImageIO.write(out, "PNG", outFlt);
		}
	}
	
	protected static void testControls(VideoDevice device) throws ControlException, UnsupportedMethod, StateException {
		System.out.println("RUNNING TEST: CONTROLS");
		ControlList controls = device.getControlList();
		for (Control control : controls.getList()) {
			switch (control.getType()) {
				case V4L4JConstants.CTRL_TYPE_STRING:
					System.out.print("String control: " + control.getName() + " - min: " + control.getMinValue() + " - max: "
							+ control.getMaxValue() + " - step: " + control.getStepValue() + " - value: ");
					try {
						System.out.println(control.getStringValue());
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
						ve.printStackTrace();
					}
					break;
				case V4L4JConstants.CTRL_TYPE_LONG:
					System.out.print("Long control: " + control.getName() + " - value: ");
					try {
						System.out.println(control.getLongValue());
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
					}
					break;
				case V4L4JConstants.CTRL_TYPE_DISCRETE:
					Map<String, Integer> valueMap = control.getDiscreteValuesMap();
					System.out.print("Menu control: " + control.getName() + " - value: ");
					try {
						int value = control.getValue();
						System.out.print(value);
						try {
							System.out.println(" (" + control.getDiscreteValueName(control.getDiscreteValues().indexOf(value)) + ")");
						} catch (Exception e) {
							System.out.println(" (unknown)");
						}
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
					}
					System.out.println("\tMenu entries:");
					for (String s : valueMap.keySet())
						System.out.println("\t\t" + valueMap.get(s) + " - " + s);
					break;
				default:
					System.out.print("Control: " + control.getName() + " - min: " + control.getMinValue() + " - max: " + control.getMaxValue()
							+ " - step: " + control.getStepValue() + " - value: ");
					try {
						System.out.println(control.getValue());
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
					}
			}
		}
		device.releaseControlList();
	}
	
	/**
	 * Initialize the GPIO, getting the pin that the LED is attached to.
	 * @param args parsed command line arguments
	 * @return LED pin
	 */
	protected static GpioPinDigitalOutput initGpio(ParsedCommandLineArguments args) {
		if (args.isFlagSet("--no-gpio"))
			//GPIO is disabled, so return null
			return null;
		//Get the GPIO object
		final GpioController gpio = GpioFactory.getInstance();
		GpioPinDigitalOutput pin;
		if (args.isFlagSet("--gpio-pin")) {
			//Get the GPIO pin by name
			String pinName = args.get("--gpio-pin");
			if (!pinName.startsWith("GPIO ") && pinName.matches("\\d+"))
				pinName = "GPIO " + pinName.trim();
			pin = gpio.provisionDigitalOutputPin(RaspiPin.getPinByName(pinName), "LED pin", PinState.LOW);
		} else {
			//Get the GPIO_01 pin 
			pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "LED pin", PinState.LOW);
		}
		System.out.println("Using GPIO pin " + pin.getPin());
		pin.setMode(PinMode.DIGITAL_OUTPUT);
		pin.setState(false);//turn it off
		return pin;
	}
	

	/**
	 * Initializes the UDP connector to the RoboRio.
	 * 
	 * @param args
	 *            Command line arguments, containing flags that modify how the
	 *            UDP connector is set up
	 * @param executor
	 *            Executor to run background tasks (such as asynchronous mDNS
	 *            resolution)
	 * @return Rio client, or null if disabled
	 * @throws SocketException
	 */
	protected static RoboRioClient initClient(ParsedCommandLineArguments args, ExecutorService executor) throws SocketException {
		if (args.isFlagSet("--no-udp")) {
			System.out.println("CLIENT DISABLED (cli)");
			return null;
		}
		int port = args.getOrDefault("--udp-port", RoboRioClient.RIO_PORT);
		int retryTime = args.getOrDefault("--mdns-resolve-retry", RoboRioClient.RESOLVE_RETRY_TIME);
		if (port < 0) {
			System.out.println("CLIENT DISABLED (port)");
			return null;
		}
		String address = args.getOrDefault("--udp-target", RoboRioClient.RIO_ADDRESS);
		System.out.println("Address: " + address);
		try {
			return new RoboRioClient(RoboRioClient.SERVER_PORT, new InetSocketAddress(address, port));
//			return new RoboRioClient(executor, retryTime, NetworkInterface.getByName("eth0"), RoboRioClient.SERVER_PORT, address, port);
		} catch (Exception e) {
			//restrict scope of broken stuff
			e.printStackTrace();
			System.err.println("CLIENT DISABLED (error)");
			return null;
		}
	}
	
	/**
	 * Initialize the image processor
	 * @param args
	 * @param httpServer
	 * @param client
	 * @return Image proccessor to handle images, or null if disabled
	 */
	protected static AbstractImageProcessor<?> initProcessor(ParsedCommandLineArguments args, final MPHttpServer httpServer, final RoboRioClient client) {
		if (args.isFlagSet("--no-process")) {
			System.out.println("PROCESSOR DISABLED");
			return null;
		}
		
		if (args.isFlagSet("--trace-contours")) {
			ContourTracer processor = new ContourTracer(width, height, polygons -> {
				for (Polygon polygon : polygons) {
					System.out.println("=> " + polygon);
					PointNode node = polygon.getStartingPoint();
					// Scale
					do {
						node = node.set(node.getX() / width, node.getY() / height);
					} while (!(node = node.next()).equals(polygon.getStartingPoint()));
				}
				if (httpServer != null)
					httpServer.offerPolygons(polygons);
			});
			Main.processor = processor;
		} else {
			ImageProcessor processor = new ImageProcessor(width, height, BOILER_TARGET_WIDTH, BOILER_TARGET_HEIGHT, rectangles-> {
				//Filter based on aspect ratio (height/width)
				//The targets for Steamworks are pretty close to 1:8, so filter out
				//things that are more square-ish
				rectangles.removeIf(rectangle-> {
					double ar = rectangle.getHeight() / rectangle.getWidth();
					return ar > (1/6f) || ar < (1/16f);
				});
				
				//print the rectangles' dimensions to STDOUT
				for (PreciseRectangle rectangle : rectangles)
					System.out.println("=> " + rectangle);
				
				//send the largest rectangle(s) to the Rio
				try {
					if (client != null) {
						if (rectangles.isEmpty()) {
							client.writeNoneFound();
						} else if (rectangles.size() == 1) {
							client.writeOneFound(rectangles.get(0));
						} else {
							client.writeTwoFound(rectangles.get(0), rectangles.get(1));
						}
					}
				} catch (IOException | NullPointerException e) {
					e.printStackTrace();
				}
				//Offer the rectangles to be put in the SSE stream
				if (httpServer != null)
					httpServer.offerRectangles(rectangles);
			}, args.isFlagSet("--save-diff"));
			Main.processor = processor;
		}
		Main.processor.start();
		enableProcessor();
		return Main.processor;
	}
	

	/**
	 * COMPUTERVISION(c)(sm): For the embetterment of computers seeing things.
	 * <p>
	 * Adjusts camera settings to optimize the image processing algorithms.
	 * Mostly just drops the exposure as low as possible, and plays around with
	 * a few other controls.
	 * </p>
	 */
	public static void enableProcessor() {
		System.out.println("ENABLING CV");
		if (processor == null) {
			if (processorEnabled)
				disableProcessor(null);
			return;
		}
		if (camera == null) {
			processorEnabled = false;
			return;
		}
		try {
			ControlList controls = camera.getControlList();
			controls.getControl("Exposure, Auto").setValue(1);
			controls.getControl("Exposure (Absolute)").setValue(19);
			controls.getControl("Contrast").setValue(10);
//			Control whiteBalanceControl = controls.getControl(don't remember what goes here);
//			whiteBalanceControl.setValue(whiteBalanceControl.getMaxValue());
			Control saturationControl = controls.getControl("Saturation");
			saturationControl.setValue(saturationControl.getMaxValue());
			Control sharpnessControl = controls.getControl("Sharpness");
			sharpnessControl.setValue(50);
			Control brightnessControl = controls.getControl("Brightness");
			brightnessControl.setValue(42);
		} catch (ControlException | UnsupportedMethod | StateException e) {
			e.printStackTrace();
		} finally {
			camera.releaseControlList();
		}
		processorEnabled = true;
	}
	
	/**
	 * PEOPLEVISION(r)(tm): The only way for people to look at things (c)(sm)(r)
	 * <p>
	 * This revolutionary technology, when enabled, embetters the vision of
	 * people looking at a LCD displaying a sequence of images transmitted over
	 * a network from a robot with a webcam, by disabling the computer image
	 * processing algorithms, and playing around with the camera's controls.
	 * When this is enabled, <strong>no image processing will be done</strong>
	 * </p>
	 * 
	 * @param server
	 */
	public static void disableProcessor(MJPEGServer server) {
		System.out.println("DISABLING CV");
		if (camera == null) {
			processorEnabled = false;
			return;
		}
		if (server != null)
			server.offerRectangles(Collections.emptyList());
		try {
			ControlList controls = camera.getControlList();
			try {
				controls.getControl("Exposure, Auto").setValue(2);
			} catch (ControlException e) {
				e.printStackTrace();
			}
			controls.getControl("Exposure (Absolute)").setValue(156);
			controls.getControl("Contrast").setValue(10);
			controls.getControl("Saturation").setValue(83);
			controls.getControl("Sharpness").setValue(50);
			controls.getControl("Brightness").setValue(42);
		} catch (ControlException | UnsupportedMethod | StateException e) {
			e.printStackTrace();
		} finally {
			camera.releaseControlList();
		}
		processorEnabled = false;
	}
	
	/**
	 * Set the JPEG quality from the camera. Tests have shown that this does
	 * <b>NOT</b> reduce the MJPEG stream's bandwidth.
	 * 
	 * @param quality
	 *            quality to set. Must be 0 to 100 (inclusive)
	 */
	public static void setQuality(int quality) {
		if (frameGrabber == null)
			return;
		System.out.println("SETTING QUALITY TO " + quality);
		frameGrabber.setJPGQuality(quality);
	}
	
	/**
	 * Create and initialize the server
	 * @param args the command line arguments
	 * @return server, if created, or null
	 * @throws IOException
	 */
	protected static MPHttpServer initServer(ParsedCommandLineArguments args, ExecutorService executor) throws IOException {
		int port = args.getOrDefault("--port", DEFAULT_PORT);
		System.out.println("Port: " + port);
		
		if (port > 0 && !args.isFlagSet("--no-server")) {
			MPHttpServer server = new MPHttpServer(port, args.getOrDefault("--moejs-dir", "../moe.js/build"));
			try {
				server.start();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return server;
		} else {
			System.out.println("SERVER DISABLED");
		}
		return null;
	}
	
	/**
	 * Binds to and initializes the camera.
	 * @param args parsed command line arguments
	 * @return the camera device, or null if <kbd>--no-camera</kbd> is set.
	 * @throws V4L4JException
	 */
	protected static VideoDevice initCamera(ParsedCommandLineArguments args) throws V4L4JException {
		String devName = args.getOrDefault("--camera", "/dev/video0");
		if (args.isFlagSet("--no-camera"))
			return null;
		System.out.print("Attempting to connect to camera @ " + devName + "...\t");
		VideoDevice device;
		try {
			device = new VideoDevice(devName);
		} catch (V4L4JException e) {
			System.out.println("ERROR");
			throw e;
		}
		Runtime.getRuntime().addShutdownHook(new Thread(()->{
			device.releaseControlList();
			device.releaseFrameGrabber();
			System.out.println("Closing dev");
			device.release(false);
		}));
		System.out.println("SUCCESS");
		return device;
	}
	
	/**
	 * Loads the CommandLineParser from inside the JAR.
	 * @return parser.
	 */
	protected static CommandLineParser loadParser() {
		try (ObjectInput in = new ObjectInputStream(Main.class.getResourceAsStream("/resources/parser.ser"))) {
			return (CommandLineParser) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			System.err.println("Error reading parser");
			e.printStackTrace();
		}
		return buildParser();
	}
	
	protected static CommandLineParser buildParser() {
		CommandLineParser parser = CommandLineParser.builder()
			.addFlag("--help", "Displays the help message and exits")
			.alias("-h", "--help")
			.alias("-?", "--help")
			.addFlag("--verbose", "Enable verbose output (not implemented)")
			.alias("-v", "--verbose")
			.addFlag("--version", "Print the version string.")
			.addFlag("--out", "Specify where to write log messages to (not implemented)")
			.addKvPair("--test", "target", "Run test by name. Tests include 'controls', 'client', and 'sse'.")
			.addKvPair("--test-images", "dir", "Directory in which images for testing are put")
			.addKvPair("--props", "file", "Specify the file to read properties from (not implemented)")
			.addKvPair("--write-props", "file", "Write properties to file, which can be passed into the --props arg in the future (not implemented)")
			.addFlag("--rebuild-parser", "Rebuilds the parser binary file")
			// Camera options
			.addKvPair("--camera", "device", "Specify the camera device file to use. Default '/dev/video0'")
			.alias("-C", "--camera")
			.addKvPair("--width", "px", "Set the width of image to capture/broadcast")
			.addKvPair("--height", "px", "Set the height of image to capture/broadcast")
			.addKvPair("--jpeg-quality", "quality", "Set the JPEG quality to request. Must be 1-100")
			.addKvPair("--fps-num", "numerator", "Set the FPS numerator. If the camera does not support the set framerate, the closest one available is chosen.")
			.addKvPair("--fps-denom", "denom", "Set the FPS denominator. If the camera does not support the set framerate, the closest one available is chosen.")
			// HTTP server options
			.addKvPair("--port", "port", "Specify the port for the HTTP server to listen on. Default 5800; a negative port number is equivalent to --no-server")
			.alias("-p", "--port")
			// GPIO options
			.addKvPair("--gpio-pin", "pin number", "Set which GPIO pin to use. Is ignored if --no-gpio is set")
			.addKvPair("--gpio-delay", "ms", "Set delay for GPIO writes")
			.addFlag("--invert-gpio", "Invert the GPIO light")
			// Image processor options
			.addKvPair("--x-skip", "px", "Number of pixels to skip on the x axis when processing sweep 1 (not implemented)")
			.addKvPair("--y-skip", "px", "Number of pixels to skip on the y axis when processing sweep 1 (not implemented)")
			.addFlag("--trace-contours", "Enable the (dev) contour tracing algorithm")
			.addFlag("--save-diff", "Save the diff image to a file (./img/delta[#].png). Requires processor.")
			.addKvPair("--target-width", "px", "Minimum width of target")
			.addKvPair("--target-height", "px", "Minimum height of target")
			// Client options
			.addKvPair("--udp-target", "address", "Specify the address to broadcast UDP packets to")
			.alias("--rio-addr", "--udp-target")
			.addKvPair("--udp-port", "port", "Specify the port to send UDP packets to. Default 5801; a negative port number is equivalent to --no-udp.")
			.alias("--rio-port", "--udp-port")
			.addKvPair("--mdns-resolve-retry", "time", "Set the interval to retry to resolve the Rio's address")
			.alias("--rio-resolve-retry", "--mdns-resolve-retry")
			// Disabling stuff options
			.addFlag("--no-server", "Disable the HTTP server.")
			.addFlag("--no-process", "Disable image processing.")
			.addFlag("--no-camera", "Do not specify a camera. This option will cause the program to not invoke v4l4j.")
			.addFlag("--no-udp", "Disable broadcasting UDP.")
			.addFlag("--no-gpio", "Disable attaching to a pin. Invoking this option will not invoke WiringPi. Note that the pin is reqired for image processing.")
			.addKvPair("--moejs-dir", "dir", "Directory to find MOE.js static files in.")
			.build();
		return parser;
	}
}
