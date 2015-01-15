import java.io.BufferedInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.io.OutputStream;


public class ServSock implements Runnable{
	Thread myThread;
	Integer id;    // Server Id
	Socket connectionSocket;
	long updateTime;  // keeps track of the last time when an update message was received on this socket
	Queue<Mmessage> outgoingMessageQueue;
	Integer StoreSize;   // To keep track of how much space the particular server is using

	public ServSock(int sendId, Socket connectSocket){
		this.id = sendId;
		this.connectionSocket = connectSocket;
		this.updateTime = Calendar.getInstance().getTimeInMillis();
		// NOTE constant number here.
		outgoingMessageQueue = new ArrayBlockingQueue<Mmessage>(50);
		StoreSize = 0;
	}

	public long getUpdateTime(){
		return updateTime;
	}
	public int getLinkID() {
		return id;
	}

	public void StartAsync() {
		if (myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}

	public synchronized void EnqueueOutgoingMessage(Mmessage msg) {
		outgoingMessageQueue.add(msg);
	}
	
	public void setStorage(int s){
		StoreSize = s;
	}
	
	public int sendMess(Mmessage msg){
		int status = 0;
		try {
			OutputStream writer = connectionSocket.getOutputStream();
			
			if(msg.getDest() == this.getLinkID()){
				byte[] sendMessage = msg.getFrameByte();
				writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
				writer.write(sendMessage);
			}
			status = 1;
		} catch (Exception e) {
			System.out.println("Server Connectin Failed");
			//We remove this server from the server list we have. It will have to be reinitialize again
			MServerCore.getInstance().ServList.remove(this);
		}
		
		return status;
		
	}
	

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(true){
			try {
				BufferedInputStream reader = new BufferedInputStream(
						connectionSocket.getInputStream());

				OutputStream writer = connectionSocket.getOutputStream();
				// TODO: Size of the multi-cast data is limited to 4096 - headers
				byte[] lenBuffer=new byte[4];
				byte[] buffer = new byte[10000];   // Limit of message to send
				int msgLength=0;
				int readLength=0;
				SimpleDateFormat ft = 
					      new SimpleDateFormat ("hh:mm:ss ");
				while (true) {
					while (reader.available() > 0) {
						reader.read(lenBuffer, 0, 4);
						msgLength = ByteBuffer.wrap(lenBuffer).getInt();
						int position = 0;
						while (position<msgLength-1){
							readLength = reader.read(buffer,position, msgLength-position);
							position+=readLength;
						}
						String msg = new String(buffer,0,msgLength);
						if (!msg.isEmpty()) {
							Mmessage ms = new Mmessage(msg.getBytes());
							if(ms.getType().equals("UP")){
								// we need to update chunk list
								this.updateTime = Calendar.getInstance().getTimeInMillis();
								String p = ms.getPayload();
								int storage = 0;
								if (p.length() > 20){
									int Offset = 0;
									
									while(Offset != p.length()){
										int fileNameLength = Integer.valueOf(p.substring(Offset, Offset + 4));
										Offset = Offset + 4;
										String ChunkName = p.substring(Offset, Offset+20);
										int maxfileSize = 20;
										Offset = Offset +20;
										int ChunkNamesize = maxfileSize - fileNameLength;
										ChunkName = ChunkName.substring(ChunkNamesize);
										String fileName = ChunkName.substring(0,ChunkName.length()-4);
										Integer fileSize = Integer.valueOf(p.substring(Offset, Offset +4));
										storage = storage + fileSize;
										Offset = Offset + 4;
										MServerCore.getInstance().touChunk(fileName, ChunkName, fileSize);
									}
								}
								
								this.setStorage(storage);
							}
						}
					}
					while (!outgoingMessageQueue.isEmpty()) {
						Mmessage msg = outgoingMessageQueue.remove();
						if(msg.getDest() == this.getLinkID()){
							byte[] sendMessage = msg.getFrameByte();
							writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
							writer.write(sendMessage);
						}
					}
					Thread.sleep(20);
				}
			} catch (Exception e) {
				System.out.println("Server: " + this.id.toString() + " is down");
				//We remove this server from the server list we have. It will have to be reinitialize again
				MServerCore.getInstance().ServList.remove(this);
			}
		}
	}
}
class ServCompare implements Comparator<ServSock>{
	@Override
	public int compare(ServSock o1, ServSock o2) {
	    return o1.StoreSize - o2.StoreSize;
	}
}

