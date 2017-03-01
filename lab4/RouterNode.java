import javax.swing.*; 

       

public class RouterNode {
  private int myID;
  private GuiTextArea myGUI;
  private RouterSimulator sim;
  private int[] costs = new int[RouterSimulator.NUM_NODES];


    /*

     */
    private int[][] distances = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES]; 
    private boolean[] is_neighbour = new boolean[RouterSimulator.NUM_NODES];

    //
    private int[] linkCosts = new int[RouterSimulator.NUM_NODES];
    private int[] firstHop = new int[RouterSimulator.NUM_NODES];


    // change this to false to reverse poisonreverse
    public static final boolean poisonreverse = true; 
  //--------------------------------------------------
  public RouterNode(int ID, RouterSimulator sim, int[] costs) {
    myID = ID;
    this.sim = sim;
    myGUI =new GuiTextArea("  Output window for Router #"+ ID + "  ");

    System.arraycopy(costs, 0, this.costs, 0, RouterSimulator.NUM_NODES);

    printDistanceTable();

    // initiate is_neighbor to false
    for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){   
	is_neighbour[i] = false;
    }

    /*
      Send a packet to all other nodes. This schelps figure out what nodes are neighboring. 
     */
    RouterPacket init = new RouterPacket(myID, 0, costs);
    for (int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
	init.destid = i;
	sendUpdate(init);
    }
  }

  //--------------------------------------------------
  public void recvUpdate(RouterPacket pkt) {
      System.out.println("recvUpdate"); 
      // int sourceid;       /* id of sending router sending this pkt */
      // int destid;         /* id of router to which pkt being sent 
      //                        (must be an immediate neighbor) */
      // int[] mincost = new int[RouterSimulator.NUM_NODES];    /* min cost to node 0 ... 3 */
      
      //keep track of if we need to update
      boolean is_changed = false;


      //update or link cost array
      if(linkCosts[pkt.sourceid] != pkt.mincost[pkt.sourceid]){
	  is_changed = true; 
	  linkCosts[pkt.sourceid] = pkt.mincost[pkt.sourceid]; 
      }

      // Neighboor array is updated with the id of the sending neighboor
      if ( !is_neighbour[pkt.sourceid] ){
      	  is_changed = true; 
      	  is_neighbour[pkt.sourceid] = true;    
      	  RouterPacket init = new RouterPacket(myID, 0, costs);
      	  for (int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
      	      init.destid = i;
      	      sendUpdate(init);
      	  }
      }

      // distances matrix is updated with the new distance vector
      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){   
	  distances[pkt.sourceid][i] = pkt.mincost[i];
      }

      // Loop through mincost to se what costs need to be updated call
      // updateLinkCost for each that needs to be changed.
      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	  if( (pkt.mincost[i] + costs[pkt.sourceid]) < costs[i] ) {
	      costs[i] = pkt.mincost[i] + costs[pkt.sourceid];
	      is_changed = true;
	  }
      }

      //update the firsthop 
      int tmp; 
      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	  tmp = linkCosts[i] + distances[i][pkt.destid];
	  if(tmp < firstHop[i] && i != myID){
	      firstHop[i] = linkCosts[i] + distances[i][pkt.destid];
	  }
      }


      //if anything changed, tell our neighbours what is up
      //will the last constructed node get an incorrect array of neighbours? 
      if(is_changed) {
	  //tell lies about cost
	  int falsecosts[] = new int[RouterSimulator.NUM_NODES];
	  System.arraycopy(costs, 0, falsecosts, 0, RouterSimulator.NUM_NODES); 
	  
	  if(poisonreverse){
	      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {      
		  //don't route back to anyone that isn't me 
		  if( falsecosts[i] != pkt.destid && falsecosts[i] != pkt.sourceid ){
		      falsecosts[i] = RouterSimulator.INFINITY; 
		  }
	      }
	  }

	  RouterPacket outgoing_pkt = new RouterPacket(myID, pkt.sourceid, falsecosts);
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {      
	      outgoing_pkt.destid = i;
	      if( is_neighbour[i] ) {
		  sendUpdate(outgoing_pkt);
	      }
	  }
      }

      
      


      // Poison reverse.
      

      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	  myGUI.print(Integer.toString(pkt.mincost[i]) + " "); 
      }

      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	  if((pkt.mincost[i] + costs[pkt.sourceid]) < costs[i] ) 
	  {
	      costs[i] = pkt.mincost[i] + costs[pkt.sourceid];  
	  }
      }

      
      printDistanceTable();
      // SendUpdate
  }
  

  //--------------------------------------------------
  private void sendUpdate(RouterPacket pkt) {
      sim.toLayer2(pkt); // Will run recive update on the next reciving node

    

  }
  

  //--------------------------------------------------
  public void printDistanceTable() {
	  myGUI.println("---\nCurrent table for " + myID +
			"  at time " + sim.getClocktime());
	  myGUI.println("\nDistance table (Y is Destinations, X is sources)\n\t"); //or are they
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print("\t"+i); 
	  }
	  myGUI.print("\n--|------------------------------------------------------------------------------------------------------------------\n"); 
	  
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print(i +"|\t"); 
	      for(int j = 0; j < RouterSimulator.NUM_NODES; ++j){

	      myGUI.print(Integer.toString(distances[i][j])); 
	      myGUI.print("\t"); 
	      }
	      myGUI.println(""); 
	  }
  }

  //--------------------------------------------------
  public void updateLinkCost(int dest, int newcost) {

      // Vi är i nod 1
      // in kommer dest 2 , new cost 5
      // cost = 5 + tiden mellan 1 och 2
      costs[dest] = newcost; 
      printDistanceTable();
  }

}

