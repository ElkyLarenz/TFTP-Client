import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

class TFTPException extends Exception
{
	public TFTPException()
	{
		super();
	}
	
	public TFTPException(String s)
	{
		super(s);
	}
}

public class WriteTest
{
	public static final String IP = "127.0.0.1";
	public static final int PORT = 69;
	
	public static final byte RRQ = 1;
	public static final byte WRQ = 2;
	public static final byte DATA = 3;
	public static final byte ACK = 4;
	public static final byte ERROR = 5;
	
	public static final int PACKET_SIZE = 516;
	public static final int DATA_SIZE = 512;
	
	public static final String MODE = "octet";
	
	public static String filename = "cool.txt";
	
	public static void main(String[] args)
	{
		wrq();
	}
	
	public static void wrq()
	{
		try {
			DatagramSocket socket = new DatagramSocket();
			InetAddress address = InetAddress.getByName(IP);
			FileInputStream file = new FileInputStream("../" + filename);
			int timeout = 5;
			
			int length = 2 + filename.length() + 1 + MODE.length() + 1;
			byte[] packet = new byte[length];
			
			byte zero = 0;
			int pos = 0;
			
			packet[pos++] = zero;
			packet[pos++] = WRQ;
			
			for ( int i = 0; i < filename.length(); i++)
			{
				packet[pos++] = (byte) filename.charAt(i);
			}
			
			packet[pos++] = zero;
			
			for (int i = 0; i < MODE.length(); i++)
			{
				packet[pos++] = (byte) MODE.charAt(i);
			}
			
			packet[pos] = zero;
			
			DatagramPacket out = new DatagramPacket(packet, packet.length, address, PORT);
			// Send write request
			socket.send(out);
			
			byte[] receivePacket = new byte[PACKET_SIZE];
			DatagramPacket ackReceive = new DatagramPacket(receivePacket, PACKET_SIZE);
			socket.receive(ackReceive);
			
			if (receivePacket[1] == (byte) 4)
			{
				System.out.println("Server is ready to receive file");
			}
			
			int bytesRead = PACKET_SIZE;
			short block = 0;
			
			while (bytesRead == PACKET_SIZE)
			{
				block++;
				byte[] blockBytes = { (byte) (block & 0xff), (byte) ((block >>> 8) & 0xff) };
				byte[] dataPacket = new byte[PACKET_SIZE];
				pos = 0;
				
				dataPacket[pos++] = zero;
				dataPacket[pos++] = DATA;
				dataPacket[pos++] = blockBytes[0];
				dataPacket[pos] = blockBytes[1];
				
				bytesRead = file.read(dataPacket, 4, DATA_SIZE) + 4;
				
				DatagramPacket outData = new DatagramPacket(dataPacket, dataPacket.length, address, PORT);
				
				socket.send(outData);
				
				while (timeout != 0)
				{
					try
					{
						// receive ack
						receivePacket = new byte[PACKET_SIZE];
						ackReceive = new DatagramPacket(receivePacket, PACKET_SIZE);
						socket.receive(ackReceive);
						
						if (receivePacket[1] == (byte) 4)
						{
							System.out.println("Got ack");
						} else
						{
							// some error probably occurred.
							break;
						}
						
						// Check that the block bytes match
						if (receivePacket[2] != blockBytes[0] || receivePacket[3] != blockBytes[1])
						{
							System.out.println("Packet lost?");
							throw new SocketTimeoutException();
						}
					} catch (SocketTimeoutException e)
					{
						System.out.println("Timeout, retry send");
						socket.send(outData);
						timeout--;
					}
				}
				
				if (timeout == 0)
				{
					throw new TFTPException();
				}
			}
			
			file.close();
			socket.close();
			
			System.out.println("Finished!");
			
		} catch (SocketException | FileNotFoundException | UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TFTPException e)
		{
			System.out.println("Connection failed.");
		}
	}
}