import ChatApp.*;          // The package containing our stubs. 
import org.omg.CosNaming.*; // HelloServer will use the naming service. 
import org.omg.CosNaming.NamingContextPackage.*; // ..for exceptions. 
import org.omg.CORBA.*;     // All CORBA applications need these classes. 
import org.omg.PortableServer.*;   
import org.omg.PortableServer.POA;
 
//import java.lang.Boolean; 
import java.util.*; 

class ChatImpl extends ChatPOA
{
    private ORB orb;

    // map for join chatusers
    Map<ChatCallback, String> activeUsers = new HashMap<ChatCallback, String>(); 

    public void setORB(ORB orb_val) {
        orb = orb_val;
    }

    public String say(ChatCallback callobj, String msg)
    {
        callobj.callback(msg);
	System.out.println(msg); 
        return ("         ....Goodbye!\n");
    }

    public int join(ChatCallback callobj, String usrname)
    {
	if (true){ // username is free
	    System.out.println(""+ usrname +" has joined the chat, kiddos"); 
	    activeUsers.put(callobj,usrname); 
	    return 0; 
	}
	else{
	    return -1; 
	}
    }



    public String list(ChatCallback callobj)
    {
	
	String userList = "There are "+  activeUsers.size() + " users active.\n"; 
	
	for( Map.Entry<ChatCallback, String> i : activeUsers.entrySet() ){
	    userList += i.getValue() + "\n"; 
	}
	    
    	return userList;
    }
}

public class ChatServer 
{
    public static void main(String args[]) 
    {
	try { 
	    // create and initialize the ORB
	    ORB orb = ORB.init(args, null); 

	    // create servant (impl) and register it with the ORB
	    ChatImpl chatImpl = new ChatImpl();
	    chatImpl.setORB(orb); 

	    // get reference to rootpoa & activate the POAManager
	    POA rootpoa = 
		POAHelper.narrow(orb.resolve_initial_references("RootPOA"));  
	    rootpoa.the_POAManager().activate(); 

	    // get the root naming context
	    org.omg.CORBA.Object objRef = 
		           orb.resolve_initial_references("NameService");
	    NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

	    // obtain object reference from the servant (impl)
	    org.omg.CORBA.Object ref = 
		rootpoa.servant_to_reference(chatImpl);
	    Chat cref = ChatHelper.narrow(ref);

	    // bind the object reference in naming
	    String name = "Chat";
	    NameComponent path[] = ncRef.to_name(name);
	    ncRef.rebind(path, cref);

	    // Application code goes below
	    System.out.println("ChatServer ready and waiting ...");
	    
	    // wait for invocations from clients
	    orb.run();
	}
	    
	catch(Exception e) {
	    System.err.println("ERROR : " + e);
	    e.printStackTrace(System.out);
	}

	System.out.println("ChatServer Exiting ...");
    }

}
