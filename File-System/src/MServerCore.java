import java.awt.List;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;


public class MServerCore implements Runnable{
	int myport = 12333;
	int servport = 12400;  // this is the starting of the port numbers for servers
	int clientport = 12500;  // this is the starting of port numbers for clients
	int chunkSize = 8192;
	int servLim = 1000;  // an id that is greater than this is a client id
	int numSer = 0;   // number of servers in the system

	ServerSocket serverSocket;
	ArrayList<ClientSock> ClientList;  // Will keep track of client sockets
	ArrayList<ServSock> ServList;  // Will keep track of client sockets
	HashMap<String,ArrayList<ChunkInfo>> metaData;
	HashMap<Integer,ArrayList<String>> servToChunk;

	public MServerCore(){
		ClientList = new ArrayList<ClientSock>();  // Will keep track of client sockets
		ServList = new ArrayList<ServSock>();  // Will keep track of client sockets
		metaData = new HashMap<String,ArrayList<ChunkInfo>>();
		servToChunk = new HashMap<Integer,ArrayList<String>>();
	}

	public void initialize(Integer s){
		numSer = s;
	}

	public ArrayList<ServSock> getServList(){
		return ServList;
	}

	public void addFileName(String f){
		ArrayList<ChunkInfo> c = new ArrayList<ChunkInfo>();
		metaData.put(f,c);
	}

	public void addServId(int h){
		ArrayList<String> c = new ArrayList<String>();
		servToChunk.put(h,c);
	}

	public void addChunkName(int h, String n){
		//Check first if there is an entry for the file in the hashMap
		if(servToChunk.containsKey(h)){
			servToChunk.get(h).add(n);
		}
		else{
			ArrayList<String> cList = new ArrayList<String>();
			cList.add(n);
			servToChunk.put(h, cList);
		}
	}

	public Integer getNumser(){
		return numSer;
	}

	public boolean hasHost(int id){
		Iterator<ServSock> iterator = ServList.iterator();
		boolean found = false;
		while(iterator.hasNext() && !found){
			ServSock l= iterator.next();
			if(l.getLinkID() == id){
				found = true;
			}
		}
		return found;
	}

	public void sortServers(){
		Collections.sort(this.ServList, new ServCompare());
	}

	public void touChunk(String fileName, String ChunkName, Integer size){
		// this method updates the last time a chunk mapping was performed
		ArrayList <ChunkInfo> cList = metaData.get(fileName);
		// First check if this particular socket is not already established
		Iterator<ChunkInfo> iterator = cList.iterator();
		boolean found = false;
		int i = 0;
		while(iterator.hasNext() && !found){
			ChunkInfo l= iterator.next();
			if(l.getChuckName().equals(ChunkName)){
				found = true;
				//Touch this entry.
				metaData.get(fileName).get(i).setTime(Calendar.getInstance().getTimeInMillis());
				metaData.get(fileName).get(i).setSize(size);
			}
			i++;
		}
	}

	public void addChunkInfo(String fileName, ChunkInfo c){
		//Check first if there is an entry for the file in the hashMap
		if(metaData.containsKey(fileName)){
			metaData.get(fileName).add(c);
		}
		else{
			ArrayList<ChunkInfo> cList = new ArrayList<ChunkInfo>();
			cList.add(c);
			metaData.put(fileName, cList);
		}
	}

	public void copyReplicas(int servId){
		ArrayList <String> cList = new ArrayList<String>();
		cList = servToChunk.get(servId);

		Iterator<String> iterator = cList.iterator();
		while(!cList.isEmpty() && iterator.hasNext()){
			String l= iterator.next();
			String fileName = l.substring(0,l.length()-4);

			ArrayList <ChunkInfo> chunkList = metaData.get(fileName);
			Iterator<ChunkInfo> newIterator = chunkList.iterator();
			boolean found = false;
			int chunkPos = 0;
			while(!chunkList.isEmpty() && newIterator.hasNext() && !found){
				ChunkInfo c = newIterator.next();
				if(c.getChuckName().equals(l)){
					found = true;
					ArrayList<Integer> hList = c.getHostList();
					boolean flag = false;
					int position = 0;   // position in host list of server to be removed

					for (int j = 0; j < 3 && !flag; j++){
						if(hList.get(j) == servId){
							flag = true;
							position = j;
						}
					}

					// Sort servers
					ArrayList<ServSock> newServList = this.ServList;
					Collections.sort(newServList, new ServCompare());

					Iterator<ServSock> Siterator = newServList.iterator();
					boolean find = false;
					
					while(!newServList.isEmpty() && Siterator.hasNext() && !find){
						ServSock s = Siterator.next();
						if(s.getLinkID() != hList.get(0) && s.getLinkID() != hList.get(1)
								&& s.getLinkID() != hList.get(2)){
							find = true;

							// Ask one of alive servers host to ask this server to create a new replica
							String t = "CC";
							int sId = hList.get(((position + 1) % 3));
							Mmessage m = new Mmessage(s.getLinkID(), t, sId,l);
							this.ServerInitialize(sId, m);
							//ServList.get(sId).EnqueueOutgoingMessage(m);
							
							// change the chunk info in metadata hashmap
							metaData.get(fileName).get(chunkPos).getHostList().set(position, sId);
						}
					}
				}
				chunkPos++;
			}

		}
		
		servToChunk.remove(servId);
	}

	// create a socket connection to socket that we did not have before
	public int ServerInitialize(Integer id, Mmessage m){
		// First check if this particular socket is not already established
		int response = 0;
		ArrayList<ServSock> newServList = this.ServList;
		Iterator<ServSock> iterator = newServList.iterator();
		boolean found = false;
		int i = 0;
		while(!newServList.isEmpty() && iterator.hasNext() && !found){
			ServSock l= iterator.next();
			if(l.getLinkID() == id){
				found = true;
				// Just add the message to the outgoing queue
				newServList.get(i).EnqueueOutgoingMessage(m);
				response = 1;
			}
			i++;
		}

		if(!found){
			try {
				String hostName = "localhost"; // HAS TO BE CHANDE TO DCID FOR ACTUAL TESTING
				Integer hId = id + 20;
				//String hostName = "dc"+padString(hId.toString(), 2);
				int portNum = servport + id;
				Socket connectSocket = new Socket(hostName, portNum);
				//System.out.println("I get here");
				if (connectSocket.isConnected()) {
					BufferedInputStream reader = new BufferedInputStream(
							connectSocket.getInputStream());

					OutputStream writer = connectSocket
							.getOutputStream();

					response = 1;
					// We first send a control message to establish the socket
					String controlM = this.padString(id.toString(), 4);
					byte[] messTosend =controlM.getBytes("ASCII");
					writer.write(messTosend);

					// create separate thread to handle this socket
					ServSock s = new ServSock(m.getDest(), connectSocket);
					s.EnqueueOutgoingMessage(m);
					ServList.add(s);
					s.StartAsync();
				}
			} catch (Exception e) {
				System.out.print("Connection to Server did not work");
				response = 0;
			}
		}

		return response;
	}

	public String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}

		return inputString;
	}


	@Override
	public void run() {
		// TODO Auto-generated method stub
		// Listen on the port
		try {
			serverSocket = new ServerSocket(myport);

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
						Iterator<ServSock> iterator = ServList.iterator();
						int i = 0;
						boolean found = false;
						while(iterator.hasNext() && !found){
							ServSock l= iterator.next();
							if(l.getLinkID() == sendId){
								ServList.remove(i);
								found = true;
							}
							i++;

						}
						ServSock s = new ServSock(sendId, peer);
						ServList.add(s);
						s.StartAsync();
					}
					else{
						// We need to spin a server socket
						ClientSock s = new ClientSock(recId,peer);
						ClientList.add(s);
						s.StartAsync();
					}
				}
				else
					peer.close();
			}
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("An error occurred");
		}
	}
	static MServerCore singleton;

	public static synchronized MServerCore getInstance() {
		if (singleton == null)
			singleton = new MServerCore();
		return singleton;
	}

}
