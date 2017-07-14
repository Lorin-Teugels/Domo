package domotics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.Random;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import domotics.SimpleClient;
import protocols.avro.Electable;
import protocols.avro.Thermostat;
import domotics.NetAddress;

	public class ThermostatClient extends SimpleClient implements Thermostat {
	public double temperature;
	private NetAddress SensorID;
	private Integer serverID;
	private String serverIP;
	private Thread PingingServer;
	private double counter;
	private double drift;
	private Random rand = new Random();
	
	public ThermostatClient(NetAddress ServerAddr, NetAddress MyAddr){
		this.SensorID = MyAddr;
		this.serverID = ServerAddr.getPort();
		this.serverIP = ServerAddr.getIPStr();
		this.PingingServer = new Thread(new clientpinger(this));
		this.counter = 0;
		this.drift = rand.nextDouble()/4; //min = 0; max = .25
		
	}

	public void run(NetAddress serverAddress){
		/*
		 * TODO stand-in server can not register new processes 
		*/
		NetAddress ID = this.getAddress();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(serverAddress.getIP(),serverAddress.getPort()));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			ID.setPort(proxy.ConnectThermostat(ID.getPort(),ID.getIPStr()));
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		System.out.println("You have ID: "+Integer.toString(this.getAddress().getPort()));
		serverRunning = new Thread(new ServerThread(this.getAddress(),this));
		serverRunning.start();
		
	}
	
	public class clientpinger implements Runnable{
		ThermostatClient ptr;
		boolean stop  = false;
		long sleeper = 3000;
		long countertime = 1000;
		long timesCounter = sleeper/countertime;
		
		public clientpinger(ThermostatClient owner){
			ptr = owner;
			//log("hello");
			ptr.temperature = ((rand.nextDouble() + 0.5) * 10) + 10;
		}
		
		public void run(){
			//ptr.CurrentuserID;
			while(! stop){

				try{
					
					for(int n = 0; n < timesCounter; n++){
						Thread.sleep(countertime);
						counter += 1 + drift;
	
					}
				
				}
				
				catch(InterruptedException e){}
				Transceiver client = null;
				try{
					//log("update: " + ptr.serverIP + ":" + ptr.serverID);
					client = new SaslSocketTransceiver(new InetSocketAddress(ptr.serverIP,ptr.serverID));
					Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
					if(rand.nextDouble()> 0.5){
						temperature = temperature + (rand.nextDouble());
					}
					else{
						temperature = temperature - (rand.nextDouble());
					}
					DecimalFormat df = new DecimalFormat("#.##");
					System.out.println(df.format(temperature));
					log("COUNTER : " + counter);
					System.out.println(df.format(counter));
					//log("temperature: " + temperature);
					proxy.UpdateTemperature(temperature);
				}
				catch(IOException e){
					//log("Ioexception thermostat pinging"+ e);
				}
				finally{
					try {
						client.close();
					} catch (IOException|NullPointerException e) {}
				}
			}
		}
		
	}
	
	@Override
	public double GetTemperature() throws AvroRemoteException{
		return temperature;
	}
	

	

	
	public static void main(String[] args){
		clientinfo info = mainstart("thermostat",args);
		
		ThermostatClient ThisSensor = new ThermostatClient(info.serverAddr, info.MyAddr);
		/*ThisSensor.SensorID = info.MyAddr;
		ThisSensor.serverID= info.serverAddr.getPort();
		ThisSensor.serverIP = info.serverAddr.getIPStr();*/
		ThisSensor.run(info.serverAddr);
		ThisSensor.PingingServer.start();
		while(true){
			int input = 0;
			try{
				input = System.in.read();
			} catch(Exception e){
				
			}
			if (input =='e'){ 
				ThisSensor.stop();
				break;
			}
		}
		
	}



	@Override
	public int getID() {
		return SensorID.getPort();
	}

	@Override
	public String getName() {
		return "thermostat";
	}

	@Override
	public NetAddress getAddress() {
		return SensorID;
	}

	@Override
	public boolean IsAlive(CharSequence IPaddr, int ID) throws AvroRemoteException {
		serverID = ID;
		serverIP = IPaddr.toString();
		//log("newID: " + ID + ":" + IPaddr.toString());
		return true;
	}

	@Override
	public Class getClientClass() {
		return Thermostat.class;
	}
	
	@Override
	public Void SetTime(double Time){
		counter += Time; 
		return null;
	}
	@Override
	public double GetTime(){
		
		return counter;
	}
}