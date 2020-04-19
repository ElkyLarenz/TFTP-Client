//-------------------------------------------------------------         EXAMPLE      --------------------------------------------------------
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class client {

	/*
	 * TFTP Protocol As per RFC 1350 opcode - operation 1 - Read request (RRQ) 2
	 * - Write request (WRQ) 3 - Data (DATA) 4 - Acknowledgment (ACK) 5 - Error
	 * (ERROR)
	 */

	private static final String TFTP_SERVER_IP = "192.168.1.11";
	private static final int TFTP_DEFAULT_PORT = 69;

	// TFTP OP Code
	private static final byte OP_RRQ = 1;
	private static final byte OP_DATAPACKET = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;

	private final static int PACKET_SIZE = 516;

	private DatagramSocket datagramSocket = null;
	private InetAddress inetAddress = null;
	private byte[] requestByteArray;
	private byte[] bufferByteArray;
	private DatagramPacket outBoundDatagramPacket;
	private DatagramPacket inBoundDatagramPacket;

	public static void main(String[] args) throws IOException {
		String fileName = "TFTP.pdf";
		client tFTPClientNet = new client();
		tFTPClientNet.get(fileName);
	}

	private void get(String fileName) throws IOException {

		// STEP0: prepare for communication
		inetAddress = InetAddress.getByName(TFTP_SERVER_IP);
		datagramSocket = new DatagramSocket();
		requestByteArray = createRequest(OP_RRQ, fileName, "octet");
		outBoundDatagramPacket = new DatagramPacket(requestByteArray,
				requestByteArray.length, inetAddress, TFTP_DEFAULT_PORT);

		// STEP 1: sending request RRQ to TFTP server fo a file
		datagramSocket.send(outBoundDatagramPacket);

		// STEP 2: receive file from TFTP server
		ByteArrayOutputStream byteOutOS = receiveFile();

		// STEP 3: write file to local disc
		writeFile(byteOutOS, fileName);
	}

	private ByteArrayOutputStream receiveFile() throws IOException {
		ByteArrayOutputStream byteOutOS = new ByteArrayOutputStream();
		int block = 1;
		do {
			System.out.println("TFTP Packet count: " + block);
			block++;
			bufferByteArray = new byte[PACKET_SIZE];
			inBoundDatagramPacket = new DatagramPacket(bufferByteArray,
					bufferByteArray.length, inetAddress,
					datagramSocket.getLocalPort());
			
			//STEP 2.1: receive packet from TFTP server
			datagramSocket.receive(inBoundDatagramPacket);

			// Getting the first 4 characters from the TFTP packet
			byte[] opCode = { bufferByteArray[0], bufferByteArray[1] };

			if (opCode[1] == OP_ERROR) {
				reportError();
			} else if (opCode[1] == OP_DATAPACKET) {
				// Check for the TFTP packets block number
				byte[] blockNumber = { bufferByteArray[2], bufferByteArray[3] };

				DataOutputStream dos = new DataOutputStream(byteOutOS);
				dos.write(inBoundDatagramPacket.getData(), 4,
						inBoundDatagramPacket.getLength() - 4);

				//STEP 2.2: send ACK to TFTP server for received packet
				sendAcknowledgment(blockNumber);
			}

		} while (!isLastPacket(inBoundDatagramPacket));
		return byteOutOS;
	}

	private void sendAcknowledgment(byte[] blockNumber) {

		byte[] ACK = { 0, OP_ACK, blockNumber[0], blockNumber[1] };

		// TFTP Server communicates back on a new PORT
		// so get that PORT from in bound packet and
		// send acknowledgment to it
		DatagramPacket ack = new DatagramPacket(ACK, ACK.length, inetAddress,
				inBoundDatagramPacket.getPort());
		try {
			datagramSocket.send(ack);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void reportError() {
		String errorCode = new String(bufferByteArray, 3, 1);
		String errorText = new String(bufferByteArray, 4,
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

	/*
	 * TFTP packet data size is maximum 512 bytes on last packet it will be less
	 * than 512 bytes
	 */
	private boolean isLastPacket(DatagramPacket datagramPacket) {
		if (datagramPacket.getLength() < 512)
			return true;
		else
			return false;
	}

	/*
	 * RRQ / WRQ packet format
	 * 
	 * 2 bytes - Opcode; string - filename; 1 byte - 0; string - mode; 1 byte -
	 * 0;
	 */
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