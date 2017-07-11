package domotics;

import java.net.InetAddress;

public class NetAddress {
	int port;
	InetAddress ip;
	public NetAddress(int inPort, String inIP){
		port = inPort;
		try{
			ip = InetAddress.getByName(inIP);
		} catch (Exception e){
			ip = null;
		}
		if(ip == null)
		 throw new RuntimeException("Null ip in NetAddress");
	}
	public String toString(){
		return "" + ip + ":" + port;
	}
	public Integer getPort(){
		return port;
	}
	
	public InetAddress getIP(){
		return ip;
	}
	
	public void setPort(int input){
		port = input;
	}
	
	public String getIPStr(){
		if (ip == null){
			return "";
		}
		return ip.getHostAddress();
	}
}
