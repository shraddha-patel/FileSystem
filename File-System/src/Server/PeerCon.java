package Server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import Client.message;

public class PeerCon implements Runnable{
	Thread myThread;
	int id;    // Server Id
	Socket connectionSocket;
	Queue<Smessage> outgoingMessageQueue;
	int servport = 12400;
	String Status = "NA";

	public PeerCon(int sendId, Socket connectSocket){
		this.id = sendId;
		this.connectionSocket = connectSocket;
		// NOTE constant number here.
		outgoingMessageQueue = new ArrayBlockingQueue<Smessage>(50);
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

	public synchronized void EnqueueOutgoingMessage(Smessage msg) {
		outgoingMessageQueue.add(msg);
	}
	
	public String sendAppMess(Smessage mToSend){
		String response = new String();
		try {
			BufferedInputStream reader = new BufferedInputStream(
					connectionSocket.getInputStream());

			OutputStream writer = connectionSocket.getOutputStream();
			
			// TODO: Size of the multi-cast data is limited to 4096 - headers
			byte[] lenBuffer=new byte[4];
			byte[] buffer = new byte[10000];   // Limit of message to send
			int msgLength=0;
			int readLength=0;
			
			//Send the create message
			byte[] sendMessage = mToSend.getFrameByte();
			writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
			writer.write(sendMessage);

			while (response.isEmpty()) {
				if (reader.available() > 0) {
					reader.read(lenBuffer, 0, 4);
					msgLength = ByteBuffer.wrap(lenBuffer).getInt();
					int position = 0;
					while (position<msgLength-1){
						readLength = reader.read(buffer,position, msgLength-position);
						position+=readLength;
					}
					
					String msg = new String(buffer,0,msgLength);
					if (!msg.isEmpty()) {
						//System.out.println("I am in the link");
						message ms = new message(msg.getBytes());
						String msType = ms.getType();

						// We need to create a file
						if (msType.equals("AS") || msType.equals("ER")){
							//System.out.println("\n" + mToSend.getfileName() + ": " + ms.getPayload());
							System.out.println("\n" + ms.getPayload());
							response = ms.getType();
						}
					}
				}

				Thread.sleep(20);
			}
		} catch (Exception e) {
			System.out.println("Connection to Server Failed");
			response = "DO";
		}
		return response;
		
	}
	
	private synchronized void handleAppMess(Smessage ms) throws IOException {
		// TODO Auto-generated method stub
		String fileName = ms.getfileName();
		String line = ms.getPayload();
		Integer servId = MetaSock.getInstance().myid;
		String dirName = "Dir"+ servId.toString();
		String path = dirName+"/"+ fileName;
		try{
 
    		File file =new File(path);
 
    		//true = append file
			FileOutputStream fos = new FileOutputStream(file,true);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			osw.write(line);
    		osw.close();
			int size = (int) file.length();
			//Need to update the size for this file
			MetaSock.getInstance().updateChunkInfo(fileName, size);

    	}catch(IOException e){
    		e.printStackTrace();
    	}
		
		//if this is successfull we return a AS message
		String t = "AS";
		System.out.println("We just appended to file: " + fileName);
		Smessage mToSend = new Smessage(1, t, ms.getId(), "Append Successful");
		this.EnqueueOutgoingMessage(mToSend);
	}
	
	public void handleRCMess(Smessage m){
		String fileName = m.getfileName();
		String dirName = "Dir"+ MetaSock.getInstance().myid.toString();
		String path = dirName+"/"+ fileName;
		String line = m.getPayload();
		try{
 
    		File file =new File(path);
    		
    		//true = append file
			FileOutputStream fos = new FileOutputStream(file,true);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			osw.write(line);
    		osw.close();
    	
			int size = (int) file.length();
			
			//Put this chunk information in the chunk list
			ChunkInfo c = new ChunkInfo(fileName, MetaSock.getInstance().myid,0, (long)size);
			MetaSock.getInstance().ChunkList.add(c);
			
			//if this is successfull we return a AS message
			String t = "AS";
			System.out.println("We just appended to file: " + fileName);
			Smessage mToSend = new Smessage(1, t, m.getId(), "Append Successful");
			this.EnqueueOutgoingMessage(mToSend);

    	}catch(IOException e){
    		e.printStackTrace();
    	}
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
				String response = "NA";
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
							Smessage ms = new Smessage(msg.getBytes());
							if(ms.getType().equals("AA"))
								this.handleAppMess(ms);
							else if(ms.getType().equals("RC"))
								this.handleRCMess(ms);
							
							else if (ms.getType().equals("AS") || ms.getType().equals("ER")){
								//System.out.println("\n" + mToSend.getfileName() + ": " + ms.getPayload());
								System.out.println("\n" + ms.getPayload());
								//response = ms.getType();
							}
						}
					}
					while (!outgoingMessageQueue.isEmpty()) {
						Smessage msg = outgoingMessageQueue.remove();
						if(msg.getDest() == this.getLinkID()){
							byte[] sendMessage = msg.getFrameByte();
							writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
							writer.write(sendMessage);
						}
					}
					Thread.sleep(20);
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}

}
