package Client;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;


public class Client{
	public static void main(String args[]) throws Exception {
		// On starting, the M-Server just know the number of servers
		int MServerport = 12333;
		String MServerName = "localhost";
		//String MServerName = "dc01";
		//IT HAS TO BE CHANDED TO DC01 FOR ACTUAL TESTING
		int servport = 12400;  // this is the starting of the port numbers for servers
		int clientport = 12500;  // this is the starting of port numbers for clients
		int chunkSize = 8192;
		Integer myId = 0;
		int servLim = 1000;  // an id that is greater than this is a client id
		int myPort = 0;
		Socket connectionSocket;
		String commandFile = new String();


		// we read our hostname and id
		try {
			String input = args[0];
			myId = Integer.valueOf(input);
			commandFile = args[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Need client id or commandFile");
			System.exit(-1);
		}

		myPort = clientport + myId;  // this is my actual port number
		myId = myId + servLim;

		// We establish a connection with the Mserver
		MServerSock msock = new MServerSock();

		try {
			Socket connectSocket = new Socket(MServerName, MServerport);
			//System.out.println("I get here");
			if (connectSocket.isConnected()) {
				BufferedInputStream reader = new BufferedInputStream(
						connectSocket.getInputStream());

				OutputStream writer = connectSocket
						.getOutputStream();

				// We first send a control message to establish the socket
				String controlM = padString(myId.toString(), 4);
				byte[] messTosend =controlM.getBytes("ASCII");
				writer.write(messTosend);

				// We create a thread to handle this socket
				msock.initialize(connectSocket, myId);

			}
		} catch (Exception e) {
			System.out.print("Connection to MServer Failed!\n");
		}

		String command = new String();
		String fileName = new String();
		Integer begOffset;
		Integer endOffset;
		Scanner in = new Scanner(System.in);
		String sourceFile = new String();

		//Open the input file
		BufferedReader br = new BufferedReader(new FileReader(commandFile));
		String line = new String();
		while ((line = br.readLine()) != null) {
			int i = 0;  //index in the instruction line
			char instruction = line.charAt(i);

			if(instruction == 'w'){
				i = i + 2;
				int j = i + 2;
				while (line.charAt(j) != '|'){
					j++;
				}
				fileName = line.substring(i,j);
				i = j + 1;
				String data = line.substring(i,line.length());
				int dataSize = data.getBytes().length;
				
				// We are going to ask the MServer to create this file in the system
				String t = "CR";
				int dest = 1;
				message m = new message(myId, t, dest, fileName);
				String response = "ER";
				response = msock.sendCreateMess(m);
				while(response.equals("ER")){
					Thread.sleep(2000);
					response = msock.sendCreateMess(m);
				}
				
				// after the create is successful, we append to the newly created file
				Thread.sleep(5000);
				
				t = "AP";
				dest = 1;
				m = new message(myId, t, dest, fileName);
				m.setPayload(data);
				m.setAppsize(dataSize);
				response = "ER";
				response = msock.sendAppMess(m);
				while(response.equals("ER")){
					Thread.sleep(2000);
					response = msock.sendAppMess(m);
				}
			}
			else if(instruction == 'a'){
				i = i + 2;
				int j = i + 2;
				while (line.charAt(j) != '|'){
					j++;
				}
				fileName = line.substring(i,j);
				i = j + 1;
				String data = line.substring(i,line.length());
				int dataSize = data.getBytes().length;
				
				// We are going to ask the MServer to create this file in the system
				String t = "AP";
				int dest = 1;
				message m = new message(myId, t, dest, fileName);
				m.setPayload(data);
				m.setAppsize(dataSize);
				String response = "ER";
				response = msock.sendAppMess(m);
				while(response.equals("ER")){
					Thread.sleep(2000);
					response = msock.sendAppMess(m);
				}
			}
			else if(instruction == 'r'){
				i = i + 2;
				int j = i + 2;
				while (line.charAt(j) != '|'){
					j++;
				}
				fileName = line.substring(i,j);
				
				//finding the offset in the file to read
				i = j + 1;
				j = i;
				while (line.charAt(j) != '|'){
					j++;
				}
				
				Integer offset = Integer.valueOf(line.substring(i,j));
				
				// finding the number of bytes to read
				i = j + 1;
				Integer numByte = Integer.valueOf(line.substring(i,line.length()));
				
				// if the read traverses chunks
				int fileOffset = offset % chunkSize;
				
				if((fileOffset + numByte) > chunkSize){
					int newOffset = offset;
					int byToread = chunkSize - newOffset;
					int leftToread = numByte - byToread;
					// We are going to ask the MServer to create this file in the system
					String t = "RE";
					int dest = 1;
					message m = new message(myId, t, dest, fileName);
					m.setAppsize(byToread);
					m.setOffset(newOffset);
					String response = "ER";
					response = msock.sendReadMess(m);
					while(response.equals("ER")){
						Thread.sleep(2000);
						response = msock.sendReadMess(m);
					}
					
					while(leftToread > 0){
						// We are going to ask the MServer to create this file in the system
						// Update offset and byte to read
						newOffset = 0;
						byToread = Math.min(leftToread,chunkSize);
						
						 t = "RE";
						dest = 1;
						m = new message(myId, t, dest, fileName);
						m.setAppsize(byToread);
						m.setOffset(newOffset);
						response = "ER";
						response = msock.sendReadMess(m);
						while(response.equals("ER")){
							Thread.sleep(2000);
							response = msock.sendReadMess(m);
						}
						
						//Update the amount left to read
						leftToread = leftToread -byToread;
					}
				}
				else{
					// We are going to ask the MServer to create this file in the system
					String t = "RE";
					int dest = 1;
					message m = new message(myId, t, dest, fileName);
					m.setAppsize(numByte);
					m.setOffset(offset);
					String response = "ER";
					response = msock.sendReadMess(m);
					while(response.equals("ER")){
						Thread.sleep(2000);
						response = msock.sendReadMess(m);
					}
				}
			}
			
			Thread.sleep(5000);

		}
		br.close();
	}

	public static String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}

		return inputString;
	}
}
