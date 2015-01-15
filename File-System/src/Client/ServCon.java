package Client;


import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class ServCon {
	Thread myThread;
	int id;    // Server Id
	Socket connectionSocket;
	Queue<message> outgoingMessageQueue;

	public ServCon(int sendId, Socket connectSocket){
		this.id = sendId;
		this.connectionSocket = connectSocket;
		// NOTE constant number here.
		outgoingMessageQueue = new ArrayBlockingQueue<message>(50);
	}

	public int getLinkID() {
		return id;
	}

/*	public void StartAsync() {
		if (myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}*/

	public synchronized void EnqueueOutgoingMessage(message msg) {
		outgoingMessageQueue.add(msg);
	}
	
	public String sendAppMess(message mToSend){
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

}
