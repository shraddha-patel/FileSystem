
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;


public class MServer {
	public static void main(String args[]) throws Exception {
		// On starting, the M-Server just know the number of servers
		int myport = 12333;
		int servport = 12400;  // this is the starting of the port numbers for servers
		int clientport = 12500;  // this is the starting of port numbers for clients
		int chunkSize = 8192;
		ServerSocket serverSocket;
		int numSer = 0;   // number of servers in the system
		int servLim = 1000;  // an id that is greater than this is a client id
		ArrayList<ServSock> ServList = new ArrayList<ServSock>();  // Will keep track of client sockets

		MServerCore.getInstance().initialize(numSer); 

		// We setup a thread to listen to socket connections
		Thread recThread = new Thread(MServerCore.getInstance());
		recThread.start();
		
		// we have a loop to check the availability of server
		while (true){
			ServList = MServerCore.getInstance().getServList();
			// First check if this particular socket is not already established
			Iterator<ServSock> iterator = ServList.iterator();
			long currentTime = Calendar.getInstance().getTimeInMillis();
			boolean flag = false;
			int i = 0;
			while(!ServList.isEmpty() && iterator.hasNext()){
				ServSock l= iterator.next();
				if((currentTime - l.getUpdateTime()) > 15000){
					// Just add the message to the outgoing queue
					Integer ServerId = l.getLinkID();
					System.out.println("Server " + ServerId.toString() + " is not active");
					MServerCore.getInstance().ServList.remove(i);
					
					// ask the other servers to create copy of chunks that were saved on this server
					MServerCore.getInstance().copyReplicas(ServerId);
					flag = true;
					
				}
				i++;
				if(flag){
					break;}
				
			}
			HashMap<String,ArrayList<ChunkInfo>> m= MServerCore.getInstance().metaData;
			
			Thread.sleep(1000);
		}
	}

}
