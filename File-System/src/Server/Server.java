package Server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

public class Server implements Runnable{

	static int state = 1;  // indicate the state of the server, 1 active, o down
	static Integer myId = 0;
	Thread myThread;
	static int task = 0;
	ServerSocket serverSocket;
	static int myPort = 0;
	int servLim = 1000;  // an id that is greater than this is a client id
	//ArrayList<ClientCon> ClientList = new ArrayList<ClientCon>();
	//ArrayList<PeerCon> ServList = new ArrayList<PeerCon>();

	public Server(int i) {
		if (myThread == null) {
			task = i;
			myThread = new Thread(this);
			myThread.start();
		}
	}

	public static void main(String args[]) throws Exception {
		// On starting, the M-Server just know the number of servers
		int MServerport = 12333;
		String MServerName = "localhost";
		//String MServerName = "dc01";
		//IT HAS TO BE CHANDED TO DC01 FOR ACTUAL TESTING
		int servport = 12400;  // this is the starting of the port numbers for servers
		int clientport = 12500;  // this is the starting of port numbers for clients
		int chunkSize = 8192;
		Socket connectionSocket;


		// we read our hostname and id
		try {
			String input = args[0];
			myId = Integer.valueOf(input);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Need process id");
			System.exit(-1);
		}
		
		myPort = servport + myId;  // this is my actual port number

		// We establish a connection with the Mserver
		MetaSock msock = MetaSock.getInstance();

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
				msock.StartAsync();

			}
		} catch (Exception e) {
			System.out.print("Creation to MServer failed!\n");
		}

		// Start the thread to create the hearbeat messages
		Server s = new Server(1);
		Thread.sleep(100);
		Server s1 = new Server(2);
		Thread.sleep(100);

		//Allow the user to stop the sending of heartbeet messages
		Scanner in = new Scanner(System.in);
/*		while(true){
			String command = new String();
			if(state == 1){
				System.out.println("Enter fail if you want the server to stop working for now");
				command = in.nextLine();
				if(command.equals("fail")){
					state = 0;
				}
			}
			else if(state == 0){
				System.out.println("Enter active if you want the server to be active again");
				command = in.nextLine();
				if(command.equals("active")){
					state = 1;
				}
			}
		}*/
	}

	public static String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}

		return inputString;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		if(task == 1){
			while(true){
				while(state == 1){
					int MServerId = 1;  // this is the id of the Mserver
					String payload = new String();
					String t = "UP"; // this message is for updating the chunklist at the M-Server

					//Create a hearbeat message
					if(!MetaSock.getInstance().getChunkList().isEmpty()){
						// First check if this particular socket is not already established
						Iterator<ChunkInfo> iterator = MetaSock.getInstance().getChunkList().iterator();
						while(iterator.hasNext()){
							ChunkInfo l= iterator.next();
							ChunkSizePair  c = new ChunkSizePair(l.getChuckName(), l.getSize());
							Integer nameLength = c.getName().length();
							payload = payload + padString(nameLength.toString(), 4)+
									c.getNameString()+c.getSizeString();
						}
					}
					else{
						payload = "NONE"; // to indicate currently does not contain any chunk
					}

					Smessage m = new Smessage(myId,t, MServerId, payload);
					MetaSock.getInstance().addMess(m);

					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		else{
			// TODO Auto-generated method stub
			// Listen on the port
			try {
				serverSocket = new ServerSocket(myPort);

				while(true){
					Socket peer = serverSocket.accept();

					//System.out.println("I get here");
					// TODO Remember modification from the assignment specs
					while (peer.getInputStream().available() <= 0)
						Thread.sleep(10);
					byte[] buffer = new byte[4]; 
					peer.getInputStream().read(buffer);

					// parsing the message received
					String controlM = new String(buffer);

					if(controlM != null){
						Integer recId = Integer.valueOf(controlM);
						if(recId <= servLim){
							// We need to spin a server socket
							Integer sendId = recId;
							
							// First check if this particular socket is not already established
							Iterator<PeerCon> iterator = MetaSock.getInstance().ServList.iterator();
							int i = 0;
							boolean found = false;
							while(iterator.hasNext() && !found){
								PeerCon l= iterator.next();
								if(l.getLinkID() == sendId){
									MetaSock.getInstance().ServList.remove(i);
									found = true;
								}
								i++;
								
							}
							PeerCon s = new PeerCon(sendId, peer);
							s.StartAsync();
							MetaSock.getInstance().ServList.add(s);
						}
						else{
							recId = Integer.valueOf(controlM);
							ClientCon s = new ClientCon(recId,peer, myId);
							MetaSock.getInstance().ClientList.add(s);
							s.StartAsync();
						}
					}
					else
						peer.close();
				}
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
