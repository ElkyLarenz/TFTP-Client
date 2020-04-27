
//-------------------------------------------------------------         EXAMPLE      --------------------------------------------------------
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class client {
	//
	private static final String TFTP_SERVER_IP = "192.168.1.40"; //IP of server
	private static final int PORT = 69;

	
	private static final byte RRQ = 1;
	private static final byte WRQ = 2;
	private static final byte DATA = 3;
	private static final byte ACK = 4;
	private static final byte ERROR = 5;

	private final static int PACKET_SIZE = 516;
	public static final int DATA_SIZE = 512;

	private DatagramSocket datagramSocket = null;
	private InetAddress inetAddress = null;
	private byte[] requestByteArray;
	private byte[] BuffByteArray;
	private DatagramPacket outBoundDatagramPacket;
	private DatagramPacket inBoundDatagramPacket;

	public static final String MODE = "octet";
	
	public static String filename2 = "TestDocElkyR.txt";
	public static void main(String[] args) throws IOException {
		String fileName = "vvlog.txt";
		//String fileName2 = "TestDocElkyR.txt";
		client tFTPClientNet = new client();
		tFTPClientNet.get(fileName);
		wrq();
		
	}

	private void get(String fileName) throws IOException {
		
		inetAddress = InetAddress.getByName(TFTP_SERVER_IP);
		datagramSocket = new DatagramSocket();
		requestByteArray = createRequest(RRQ, fileName, "octet");
		outBoundDatagramPacket = new DatagramPacket(requestByteArray,
				requestByteArray.length, inetAddress, PORT);

		//SENDS request RRQ to TFTP server fo a file
		datagramSocket.send(outBoundDatagramPacket);
		//
		ByteArrayOutputStream byteOutOS = receiveFile();
		//
		writeFile(byteOutOS, fileName);
	}

	public static void wrq()
	{
		try {
			DatagramSocket socket = new DatagramSocket();
			InetAddress address = InetAddress.getByName(TFTP_SERVER_IP);
			FileInputStream file = new FileInputStream(filename2);
			int timeout = 5;
			
			int length = 2 + filename2.length() + 1 + MODE.length() + 1;
			byte[] packet = new byte[length];
			
			byte zero = 0;
			int pos = 0;
			
			packet[pos++] = zero;
			packet[pos++] = WRQ;
			
			for ( int i = 0; i < filename2.length(); i++)
			{
				packet[pos++] = (byte) filename2.charAt(i);
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
			
			e.printStackTrace();
		} catch (TFTPException e)
		{
			System.out.println("Connection failed.");
		}
	}

	private ByteArrayOutputStream receiveFile() throws IOException {
		ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
		int block = 1;
		do {
			System.out.println("TFTP Packet count: " + block);
			block++;
			BuffByteArray = new byte[PACKET_SIZE];
			inBoundDatagramPacket = new DatagramPacket(BuffByteArray,
					BuffByteArray.length, inetAddress,
					datagramSocket.getLocalPort());
			
			//
			datagramSocket.receive(inBoundDatagramPacket);

			
			byte[] opCode = { BuffByteArray[0], BuffByteArray[1] };

			if (opCode[1] == ERROR) {
				reportError();
			} else if (opCode[1] == DATA) {
				// Check for the TFTP packets block number
				byte[] blockNumber = { BuffByteArray[2], BuffByteArray[3] };

				DataOutputStream dos = new DataOutputStream(byteOutOS);
				dos.write(inBoundDatagramPacket.getData(), 4,
						inBoundDatagramPacket.getLength() - 4);

				
				sendAcknowledgment(blockNumber);
			}

		} while (!isLastPacket(inBoundDatagramPacket));
		return byteOutOS;
	}

	

	private void sendAcknowledgment(byte[] blockNumber) {

		byte[] bACK = { 0, ACK, blockNumber[0], blockNumber[1] };

		
		DatagramPacket ack = new DatagramPacket(bACK, bACK.length, inetAddress,
				inBoundDatagramPacket.getPort());
		try {
			datagramSocket.send(ack);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void reportError() {
		String errorCode = new String(BuffByteArray, 3, 1);
		String errorText = new String(BuffByteArray, 4,
				inBoundDatagramPacket.getLength() - 4);
		System.err.println("Error: " + errorCode + " " + errorText);
	}

	private void writeFile(ByteArrayOutputStream baoStream, String fileName) {
		try {
			OutputStream outputStream = new FileOutputStream(fileName);
			baoStream.writeTo(outputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	private boolean isLastPacket(DatagramPacket datagramPacket) {
		if (datagramPacket.getLength() < 512)
			return true;
		else
			return false;
	}

	
	private byte[] createRequest(final byte opCode, final String fileName,
			final String mode) {
		byte zeroByte = 0;
		int rrqByteLength = 2 + fileName.length() + 1 + mode.length() + 1;
		byte[] rrqByteArray = new byte[rrqByteLength];

		int position = 0;
		rrqByteArray[position] = zeroByte;
		position++;
		rrqByteArray[position] = opCode;
		position++;
		for (int i = 0; i < fileName.length(); i++) {
			rrqByteArray[position] = (byte) fileName.charAt(i);
			position++;
		}
		rrqByteArray[position] = zeroByte;
		position++;
		for (int i = 0; i < mode.length(); i++) {
			rrqByteArray[position] = (byte) mode.charAt(i);
			position++;
		}
		rrqByteArray[position] = zeroByte;
		return rrqByteArray;
	}
}