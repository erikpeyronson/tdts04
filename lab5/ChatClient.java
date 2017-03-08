import ChatApp.*;          // The package containing our stubs
import org.omg.CosNaming.*; // HelloClient will use the naming service.
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CORBA.*;     // All CORBA applications need these classes.
import org.omg.PortableServer.*;   
import org.omg.PortableServer.POA;

import java.io.*;
import java.util.Scanner; 
import java.util.StringTokenizer; 

 
class ChatCallbackImpl extends ChatCallbackPOA
{
    private ORB orb;

    public void setORB(ORB orb_val) {
        orb = orb_val;
    }

    public void callback(String notification)
    {
        System.out.println(notification);
    }
}

public class ChatClient
{
    static Chat chatImpl;
    
    public static void main(String args[])
    {
	try {
	    // create and initialize the ORB
	    ORB orb = ORB.init(args, null);

	    // create servant (impl) and register it with the ORB
	    ChatCallbackImpl chatCallbackImpl = new ChatCallbackImpl();
	    chatCallbackImpl.setORB(orb);

	    // get reference to RootPOA and activate the POAManager
	    POA rootpoa = 
		POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();
	    
	    // get the root naming context 
	    org.omg.CORBA.Object objRef = 
		orb.resolve_initial_references("NameService");
	    NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
	    
	    // resolve the object reference in naming
	    String name = "Chat";
	    chatImpl = ChatHelper.narrow(ncRef.resolve_str(name));
	    
	    // obtain callback reference for registration w/ server
	    org.omg.CORBA.Object ref = 
		rootpoa.servant_to_reference(chatCallbackImpl);
	    ChatCallback cref = ChatCallbackHelper.narrow(ref);
	    
	    // Application code goes below
	    /*
	      while loop som väntar på input, och då "kommando asdasdasd asd" t.ex say memes
	      cin >> kommandorad; 
	    */ 
	    Scanner sc = new Scanner(System.in);
	    String prefix = ""; 
	    String command = "";
	    boolean quit = false; 



	    while(!quit){
		if(sc.hasNext()){
		    prefix = sc.next(); 
		    
		    if( sc.hasNextLine() ){
			System.out.println("if"); 

			if( sc.hasNextLine() ){
			    System.out.println("if2"); 
			    //sc.skip(" "); 
			    command = sc.nextLine(); 
			}
		    }
		    else{
			System.out.println("else"); 
			command = ""; 
		    }

		    System.out.println("---"); 
		    System.out.println("prefix: "+prefix);
		    System.out.println("command: "+command); 
		    System.out.println("---"); 

		    switch (prefix){
		    case "say": 
			String chat = chatImpl.say(cref, "<me> " + command);
			break; 
		    case "join":
			if(chatImpl.join(cref, command) == 0)
			    System.out.println("Joined just fine jimbo..."); 
			else
			    System.out.println("Didn't join...."); 
			break;
		    case "list":
			System.out.println(chatImpl.list(cref)); 
			//remove from join
			break; 
		    case "quit":
			quit = true; 
			System.out.println("Exiting, kid..."); 
			//remove from join
			break; 
		    default:
			System.out.println("Command wasn't recognized, kid..."); 
			break; 
		    }

		    prefix = ""; 
		    command = ""; 
		    sc.reset(); 
		}
	    }

	    //String chat = chatImpl.say(cref, "\n" + st.nextToken("\n"));

	    // chatImpl.list(cref); 

	    
	} catch(Exception e){
	    System.out.println("ERROR : " + e);
	    e.printStackTrace(System.out);
	}
    }
}
