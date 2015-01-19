import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class TranslateServer {
	private static final int portNumber = 4444;

	ServerSocketChannel ssc;
	private Selector selector;
	public Map<String, String> wordDict = new HashMap<String, String>();
	
	// Read buffer
	private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
	
	// Track data to be written
	private Map<SocketChannel,byte[]> dataTracking = new HashMap<SocketChannel, byte[]>();
	
	// Accept new client and prepare for reading
	private void doAccept(SelectionKey selKey) 
	{
		ServerSocketChannel server = (ServerSocketChannel) selKey.channel();
		SocketChannel clientChannel;
		try
		{
			clientChannel = server.accept();
			clientChannel.configureBlocking(false);
			// Register for reading
			SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
			
			InetAddress clientAddress = clientChannel.socket().getInetAddress();
			System.out.println("Accepted connection from " + clientAddress.getHostAddress()); 
		}
		catch (Exception e)
		{
			System.out.println("Failed to accept new client");
			e.printStackTrace();
		}
	}
	
	// Read from a client
	private void doRead(SelectionKey selKey)
	{
		SocketChannel channel = (SocketChannel)selKey.channel();
		// prepare buffer
		readBuffer.clear();
		// attempt to read
		int len;
		try
		{
			len = channel.read(readBuffer);
			if (len < 0)
			{
				System.out.println("Nothing to be read");
				disconnect(selKey);
				return;
			}
		}
		catch (Exception e)
		{
			System.out.println("Failed to read from client");
			e.printStackTrace();
			return;
		}
		// try translate the user input and add to hash map
		readBuffer.flip();
		
		byte[] data = new byte[len];
		readBuffer.get(data, 0, len);
		String input = new String(data, Charset.forName("UTF-8"));
		System.out.println("Received " + input);
		
		// translate
		String output;
		if(wordDict.containsKey(input))
		{
			output = wordDict.get(input);
		}
		else
		{
			output = "Translate Error";
		}
		dataTracking.put(channel, output.getBytes());
		selKey.interestOps(SelectionKey.OP_WRITE);
	}
	
	private void doWrite(SelectionKey selKey)
	{
		SocketChannel channel = (SocketChannel) selKey.channel();
		byte[] data = dataTracking.get(channel);
		ByteBuffer bBuffer = ByteBuffer.wrap(data);
		try 
		{
			int len = channel.write(bBuffer);
			dataTracking.remove(channel);
			if (len == -1)
			{
				disconnect(selKey);
				return;
			}
		}
		catch(Exception e)
		{
			System.out.println("Failed to write to client");
			e.printStackTrace();
		}
		selKey.interestOps(SelectionKey.OP_READ);
	}
	
	private void disconnect(SelectionKey selKey)
	{
		SocketChannel channel = (SocketChannel)selKey.channel();
		InetAddress clientAddress = channel.socket().getInetAddress();
		try{
			channel.close();
			selKey.cancel();
		}
		catch (Exception e)
		{
			System.out.println("Failed to close client socket channel.");
            e.printStackTrace();
		}
	}
	
	
	private void startServer() throws Exception
	{
		// init dictionary
		wordDict.put("zero", "0");
		wordDict.put("one", "一");
		wordDict.put("two", "二");
		wordDict.put("three", "三");
		wordDict.put("four", "四");
		wordDict.put("five", "五");
		wordDict.put("six", "六");
		wordDict.put("seven", "七");
		wordDict.put("eight", "八");
		wordDict.put("nine", "九");
		wordDict.put("ten", "十");
		
		// Create non blocking server socket
		selector = SelectorProvider.provider().openSelector();
		ssc = ServerSocketChannel.open();
		// Bind the server to the socket
		InetSocketAddress address = new InetSocketAddress(portNumber);
		ssc.socket().bind(address);
		ssc.configureBlocking(false);
		
		// Register socket for select events
		SelectionKey acceptKey = ssc.register(selector, SelectionKey.OP_ACCEPT);
		
		while(true)
		{	
			selector.select();
			Set readyKeys = selector.selectedKeys();
			Iterator iter = readyKeys.iterator();
			while(iter.hasNext())
			{
				SelectionKey selKey = (SelectionKey) iter.next();
				iter.remove();
				if (selKey.isAcceptable())
				{
					doAccept(selKey);
				}
				if (selKey.isValid() && selKey.isReadable())
				{
					doRead(selKey);
				}
				if (selKey.isValid() && selKey.isWritable())
				{
					doWrite(selKey);
				}
			}
		}
	}

	public static void main (String[] args) 
	{
		TranslateServer translateServer = new TranslateServer();
		try 
		{
			translateServer.startServer();
		}
		catch (Exception e)
		{
			System.out.println("Exception caught, exiting server");
			e.printStackTrace();
		}
	}
}
