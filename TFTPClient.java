import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
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

public class TFTPClient
{
	public static final String IP = "199.17.161.104";
	public static final int PORT = 69;
	
	public static final byte RRQ = 1;
	public static final byte WRQ = 2;
	public static final byte DATA = 3;
	public static final byte ACK = 4;
	public static final byte ERROR = 5;
	
	public static final int PACKET_SIZE = 516;
	public static final int DATA_SIZE = 512;
	
	public static final String MODE = "octet";
	
	public static String receivefilename = "primes.txt";
	public static String sendfilename = "TestDocElkyR.txt";
	
	public static void main(String[] args)
	{
		rrq();
		wrq();
	}
	
	public static void rrq()
	{
		try {
			DatagramSocket socket = new DatagramSocket();
			InetAddress address = InetAddress.getByName(IP);
			
			int length = 2 + receivefilename.length() + 1 + MODE.length() + 1;
			byte[] packet = new byte[length];
			
			byte zero = 0;
			int pos = 0;
			
			packet[pos++] = zero;
			packet[pos++] = RRQ;
			
			for (int i = 0; i < receivefilename.length(); i++)
			{
				packet[pos++] = (byte) receivefilename.charAt(i);
			}
			
			packet[pos++] = zero;
			
			for (int i = 0; i < MODE.length(); i++)
			{
				packet[pos++] = (byte) MODE.charAt(i);
			}
			
			packet[pos] = zero;
			
			DatagramPacket out = new DatagramPacket(packet, packet.length, address, PORT);
			
			socket.send(out);
			
			// receive the file
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			int block = 1;
			boolean gotFinalPacket = false;
			
			while (!gotFinalPacket)
			{
				block++;
				byte[] buffByteArray = new byte[PACKET_SIZE];
				checkError(buffByteArray);
				DatagramPacket inPacket = new DatagramPacket(buffByteArray, buffByteArray.length, address, socket.getLocalPort());
				
				socket.receive(inPacket);
				
				if (inPacket.getLength() < 512)
					gotFinalPacket = true;
				
				if (buffByteArray[1] == DATA)
				{
					byte[] blockNumber = { buffByteArray[2], buffByteArray[3] };
					
					DataOutputStream outStream = new DataOutputStream(stream);
					outStream.write(inPacket.getData(), 4, inPacket.getLength() - 4);
					
					byte[] ack = { 0, ACK, blockNumber[0], blockNumber[1]};
					
					DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, address, inPacket.getPort());
					
					socket.send(ackPacket);
				}
			}
			
			// Write file to disk
			OutputStream outStream = new FileOutputStream(receivefilename);
			stream.writeTo(outStream);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void wrq()
	{
		try {
			DatagramSocket socket = new DatagramSocket();
			InetAddress address = InetAddress.getByName(IP);
			FileInputStream file = new FileInputStream(sendfilename);
			int timeout = 5;
			
			int length = 2 + sendfilename.length() + 1 + MODE.length() + 1;
			byte[] packet = new byte[length];
			
			byte zero = 0;
			int pos = 0;
			
			packet[pos++] = zero;
			packet[pos++] = WRQ;
			
			for ( int i = 0; i < sendfilename.length(); i++)
			{
				packet[pos++] = (byte) sendfilename.charAt(i);
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
			checkError(receivePacket);
			
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
						checkError(receivePacket);
						
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
							System.out.println("Got block " + (int) receivePacket[2] + " " + (int) receivePacket[3]);
							System.out.println("Expected block " + (int) blockBytes[0] + " " + (int) blockBytes[1]);
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
			e.printStackTrace();
		} catch (TFTPException e)
		{
			System.out.println("Connection failed.");
		}
	}
	
	static void checkError(byte[] receivePacket)
	{
		if (receivePacket[1] == (byte) 5)
		{
			String errorMessage = null;
			char[] messArray = new char[receivePacket.length - 4];
			
			int errorcode = (int) receivePacket[3];
			
			switch(errorcode)
			{
			case 0:
				errorMessage = "Not defined, see error message";
				break;
			case 1:
				errorMessage = "File not found";
				break;
			case 2:
				errorMessage = "Access violation";
				break;
			case 3:
				errorMessage = "Disk full or allocation exceeded";
				break;
			case 4:
				errorMessage = "Illegal TFTP operation";
				break;
			case 5:
				errorMessage = "Unknown transfer ID";
				break;
			case 6:
				errorMessage = "File already exists";
				break;
			case 7:
				errorMessage = "No such user";
				break;
			}
			
			for (int i = 4; i < receivePacket.length - 1; i++)
			{
				char chara = (char) receivePacket[i];
				messArray[i-4] = chara;
			}
			
			String errorstring = new String(messArray);
			
			System.out.println("Error code " + errorcode + ": " + errorMessage);
			System.out.println(errorstring);
		}
	}
}