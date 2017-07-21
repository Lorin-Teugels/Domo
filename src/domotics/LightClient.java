package domotics;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import domotics.SimpleClient;
import protocols.avro.Electable;
import protocols.avro.Light;
import domotics.NetAddress;


public class LightClient extends SimpleClient implements Light{
	private boolean state;

	private NetAddress lightID;

	

	public LightClient(){
		state = false;
	}
	
	public void run(NetAddress serverAddress){
		NetAddress ID = this.getAddress();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(serverAddress.getIP(),serverAddress.getPort()));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			ID.setPort(proxy.ConnectLight(ID.getPort(),ID.getIPStr()));
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			System.exit(1);
		}

		System.out.println("You have ID: "+Integer.toString(this.getAddress().getPort()));
		serverRunning = new Thread(new ServerThread(this.getAddress(),this));
		serverRunning.start();
		
	}
	
	@Override
	public Void LightSwitch() throws AvroRemoteException{
		state = ! state;
		if(state){
			System.out.println("The light is on.");
		}
		else{
			System.out.println("The light is off.");
		}
		return null;
	}
	
	@Override
	public boolean GetLightState() throws AvroRemoteException{
		return state;
	}
	

	
	public static void main(String[] args){
		clientinfo info = mainstart("light",args);
		
		LightClient thisLight = new LightClient();
		thisLight.lightID = info.MyAddr;
		thisLight.run(info.serverAddr);
		while(true){
			int input = 0;
			try{
				input = System.in.read();
			} catch(Exception e){
				
			}
			if (input =='e'){ 
				thisLight.stop();
				break;
			}
		}
		
	}



	@Override
	public int getID() {
		return lightID.getPort();
	}

	@Override
	public String getName() {
		return "lights";
	}

	@Override
	public NetAddress getAddress() {
		return lightID;
	}

	@Override
	public Class getClientClass() {
		return Light.class;
	}
}