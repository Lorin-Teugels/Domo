package domotics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import java.util.Vector;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;

import domotics.ElectableClient;
import protocols.avro.Electable;
import protocols.avro.SmartFridge;
import domotics.UserClient;
import domotics.NetAddress;


public class SmartFridgeClient extends ElectableClient implements SmartFridge {
	private NetAddress ServerID = null;
	private List<CharSequence> contents = new Vector<CharSequence>();
	private boolean open = false;
	public NetAddress currentuserID = null;
	private Thread serverThread = null;
	private Thread pinginguser;

	
	SmartFridgeClient(NetAddress server, NetAddress thisAddr){
		ServerID = server;
		selfID = thisAddr;
	}


	public int getID(){
		return selfID.getPort();
	}
	public  String getName(){
		return "fridges";
	}
	
	/*
	 * Starts server after this fridge was elected.
	 */
	public class RunServer implements Runnable{
		NetAddress ID;
		SmartFridge ptr;
		public RunServer(NetAddress aboveID, SmartFridge above){
			ID = aboveID;
			ptr = above;
		}
		public void run(){
			try{
				log("listening for fridge on: " + ID.getIP() + " " + ID.getPort());
				server = new SaslSocketServer(new SpecificResponder(SmartFridge.class, ptr),new InetSocketAddress(ID.getIP(),ID.getPort()));
			} catch(IOException e){
				System.err.println("[error] Failed to start server");
				e.printStackTrace(System.err);
				System.exit(1);
			}
			server.start();
			try{
				server.join();
			} catch(InterruptedException e){}
		}
		
		public void stop(){
			server.close();
		}
	}
	
	/*
	 * Class that pings client that opened the fridge, to ensure the fridge closes when user dies (priorities amirite).
	 */
	public class ClientPinger implements Runnable{
		SmartFridgeClient ptr;
		boolean stop  = false;
		int missescounter = 0;
		long sleeper = 3000;
		int missesallowed = 2;
		
		public ClientPinger(SmartFridgeClient owner){
			ptr = owner;
		}
		
		public void run(){
			//ptr.CurrentuserID;
			stop = false;
			while(! stop){
				missescounter++;
				log("PINGING CLIENT");
				try{
					
				Thread.sleep(sleeper);
				
				}
				
				catch(InterruptedException e){}
				if(ptr.open){
					try{
						Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ptr.currentuserID.getIP(),ptr.currentuserID.getPort()));
						UserClient proxy = (UserClient) SpecificRequestor.getClient(UserClient.class, client);
	
						if(proxy.IsAlive(ServerIP,ServerID.getPort())){
							missescounter--;
						}
						client.close();
					}
					catch(IOException e){}
					if(missescounter >= missesallowed){
						stop = true;
						ptr.CloseFridge(ptr.currentuserID.getPort());
						
					}
				}
				else{
					stop = true;
				}
			}
		}
				
	}
	
	@Override
	public List<CharSequence> GetContents() throws AvroRemoteException{
		//Map<CharSequence, List<Integer>> AllClients = new HashMap<CharSequence, List<Integer>>();
		log("Smartfridge list");
		List<CharSequence> allContents = this.contents;
		log("Smartfridge list returning " + allContents );
		return allContents;
	}
	
	@Override
	public synchronized boolean OpenFridge(int UserID,CharSequence IP){
		//Map<CharSequence, List<Integer>> AllClients = new HashMap<CharSequence, List<Integer>>();
		//Vector<String> allContents = this.contents;
		if(this.open == false){
			this.open = true;
			this.pinginguser = new Thread(new ClientPinger(this));
			this.pinginguser.start();
		}
		else{
			return false;
		}
		NetAddress userAddr = new NetAddress(UserID,String.valueOf(IP));
		this.currentuserID = userAddr;
		return true;
	}
	
	@Override
	public Void AddItem(int UserID, CharSequence item){
		//Map<CharSequence, List<Integer>> AllClients = new HashMap<CharSequence, List<Integer>>();
		//Vector<String> allContents = this.contents;
		if(this.currentuserID.getPort() == UserID){
			this.contents.add(item);
		}
		return null;
	}
	
	@Override
	public Void RemoveItem(int UserID, CharSequence item){
		//Map<CharSequence, List<Integer>> AllClients = new HashMap<CharSequence, List<Integer>>();
		//Vector<String> allContents = this.contents;
		if(this.currentuserID.getPort() == UserID){
			if(contents.contains(item)){
				contents.remove(item);
				if(this.contents.isEmpty()){
					//send message to controller
					try{
						Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerID.getIP(),ServerID.getPort()));
						Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
						proxy.FridgeIsEmpty(selfID.getPort());
						client.close();
					} catch(IOException e){
						System.err.println("Error connecting to server");
						e.printStackTrace(System.err);
					}
				}
			}
		}
		return null;
	}
	
	@Override
	public Void CloseFridge(int UserID){
		//Map<CharSequence, List<Integer>> AllClients = new HashMap<CharSequence, List<Integer>>();
		//Vector<String> allContents = this.contents;
		if(this.currentuserID.getPort() == UserID){
			this.open= false;
			this.currentuserID = null;

		}
		return null;
	}
	
	public void start(){
		log("connecting to: " + ServerID.getIP() +" " +ServerID.getPort() );
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerID.getIP(),ServerID.getPort()));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			selfID.setPort(proxy.ConnectFridge(selfID.getPort(),selfID.getIPStr()));
			log("portandIp after connect: "+selfID.getPort() + ":" +selfID.getIPStr());
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			/*e.printStackTrace(System.err);
			System.exit(1);*/

		}
		System.out.println("You have ID: "+Integer.toString(selfID.getPort()));
		serverThread = new Thread(new RunServer(selfID,this));
		serverThread.start();
	}
	
	public static void main(String[] args){
		clientinfo info = mainstart("fridge" , args);


		SmartFridgeClient fridge = new SmartFridgeClient(info.serverAddr, info.MyAddr);
		fridge.start();
		
		fridge.standby(COUNTDOWN);
		
		while(true){
			int input = 0;
			try{
				input = System.in.read();
			} catch(Exception e){
				
			}
			if (input =='e'){ 
				fridge.stop();
				break;
			}
		}
	}
	

	@Override
	public boolean ConnectToClient(int ClientID) throws AvroRemoteException {
		//unused method
			return false;
	}
}
