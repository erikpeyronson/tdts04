import javax.swing.*; 

       

public class RouterNode {
  private int myID;
  private GuiTextArea myGUI;
  private RouterSimulator sim;
  private int[] costs = new int[RouterSimulator.NUM_NODES];


    /*

     */
    private int[][] distances = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES]; 
    //private boolean[] is_neighbour = new boolean[RouterSimulator.NUM_NODES];

    //
    private int[] linkCosts = new int[RouterSimulator.NUM_NODES];
    private int[] firstHop = new int[RouterSimulator.NUM_NODES];


    // change this to false to reverse poisonreverse
    public static final boolean poisonreverse = !false; //!!!!!!!true; 
    //--------------------------------------------------
    public RouterNode(int ID, RouterSimulator sim, int[] costs) {





	myID = ID;
	this.sim = sim;
	myGUI =new GuiTextArea("  Output window for Router #"+ ID + "  ");


	System.arraycopy(costs, 0, linkCosts, 0, RouterSimulator.NUM_NODES);



	System.arraycopy(costs, 0, this.costs, 0, RouterSimulator.NUM_NODES);

	printDistanceTable();


	// initiate firstHop to the direct route
	// initiate every non-free path to INFINITY 2008
	for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){   

	    if(linkCosts[i] != RouterSimulator.INFINITY)
		firstHop[i] = i; 
	    else
		firstHop[i] = RouterSimulator.INFINITY;
	    	for(int j = 0; j < RouterSimulator.NUM_NODES; ++j){   
		    if( i != j )
			distances[i][j] = RouterSimulator.INFINITY;
		}
	}


	/*
	  Send a packet to all other nodes. This schelps figure out what nodes are neighboring. 
	*/
	RouterPacket init = new RouterPacket(myID, 0, costs);
	for (int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
	    init.destid = i;
	    if( linkCosts[i] != RouterSimulator.INFINITY &&i != myID )
	    sendUpdate(init);
	}


 

    }

  //--------------------------------------------------
  public void recvUpdate(RouterPacket pkt) {
      System.out.println("recvUpdate"); 
      
      //keep track of if we need to update
      boolean is_changed = false;
      myGUI.println("from: " + Integer.toString(pkt.sourceid) + " to: "+ Integer.toString(pkt.destid)); 


      // update the entries in distance matrix that are not ours
      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){   
	  if(distances[pkt.sourceid][i] != pkt.mincost[i])
	      {
		  is_changed = true; 
		  distances[pkt.sourceid][i] = pkt.mincost[i];	  
	      }
      }

      if(is_changed){
	  is_changed = false; 
	  // recalculate and update our entry in distances
	  for(int des = 0; des < RouterSimulator.NUM_NODES; ++des){   

	      // if the distance to destination isnt the same as the
	      // distance to a given firsthop to that destination, then
	      // change the distance to our destination given the firsthop
	      if ( des != myID ){
		  if (firstHop[des] != RouterSimulator.INFINITY
		      && distances[myID][des] != distances[firstHop[des]][des] + distances[myID][firstHop[des]])
		      {
			  is_changed = true;
			  distances[myID][des] = distances[firstHop[des]][des] + distances[myID][firstHop[des]];
		      }
	      

		  // if our linkcost is the cheapers then just use that 
		  if(linkCosts[des] < distances[myID][des]){
		      is_changed = true; 
		      distances[myID][des] = linkCosts[des]; 
		      //costs = distances[myID];
		      firstHop[des] = des; 
		  }


		  //update the firsthops
		  for(int indes = 0; indes < RouterSimulator.NUM_NODES; ++indes){
		      //
		      if( distances[myID][indes] > distances[myID][des] + distances[des][indes] 
			  //&& linkCosts[des] != RouterSimulator.INFINITY
			  )
			  {
			      is_changed = true; 
			      distances[myID][indes] = distances[myID][des] + distances[des][indes];
			      //costs = distances[myID];
			      firstHop[indes] = firstHop[des]; 
			  }
		  }
	      }

	      // for(int indes = 0; des < RouterSimulator.NUM_NODES; ++des){
	      // 	  if ( //distances[myID][des] > linkCosts[des] && 
	      // 	      firstHop[des] == des )
	      // 	      distances[myID][des] = linkCosts[des]; 
	      // }
	      
	  
	  
	  }


   
	  //if anything changed, tell our neighbours what is up
	  //will the last constructed node get an incorrect array of neighbours? 
	  if(is_changed) {
	  
	      myGUI.println("bricksquad"); 
	      //tell lies about cost
	      int falsecosts[] = new int[RouterSimulator.NUM_NODES];
	      System.arraycopy(distances[myID], 0, falsecosts, 0, RouterSimulator.NUM_NODES); 
	  
	      RouterPacket outgoing_pkt = new RouterPacket(myID, pkt.sourceid, falsecosts);
	      if(poisonreverse){
	
		  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
		      for(int j = 0; j < RouterSimulator.NUM_NODES; ++j) {
			  if(firstHop[j] == i)
			      falsecosts[j] = RouterSimulator.INFINITY; 
			  else
			      falsecosts[j] = distances[myID][j]; 
		      }
		  }

	      }
	      else{
		  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {      
		      outgoing_pkt.destid = i;
		      if( linkCosts[i] != RouterSimulator.INFINITY && i != myID) {
			  sendUpdate(outgoing_pkt);
		      }
		  }
	      }

	  }

      }

      
      


      
      printDistanceTable();

  }
  

  //--------------------------------------------------
  private void sendUpdate(RouterPacket pkt) {
      sim.toLayer2(pkt); // Will run recive update on the next reciving node

  }
  

  //--------------------------------------------------
  public void printDistanceTable() {


	  myGUI.println("\n\nCurrent table for " + myID +
			"  at time " + sim.getClocktime());
	  myGUI.println("\nDistance table (Y is Destinations, X is sources)\n\t"); //or are they
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print("\t"+i); 
	  }
	  myGUI.print("\n--|---------------------------------------------------");
	  myGUI.print("-------------------------------------------------------\n"); 
	  
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print(i +"|\t"); 
	      for(int j = 0; j < RouterSimulator.NUM_NODES; ++j){

	      myGUI.print(Integer.toString(distances[j][i])); 
	      myGUI.print("\t"); 
	      }
	      myGUI.println(""); 
	  }




	  myGUI.println("Distance vector"); //or are they
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print("\t"+i); 
	  }
	  myGUI.print("\n--|---------------------------------------------------");
	  myGUI.print("-------------------------------------------------------\n\t"); 
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print(Integer.toString(distances[myID][i]));
	      myGUI.print("\t"); 
	  }
	  myGUI.println(""); 

	  myGUI.println("First Hop"); //or are they
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print("\t"+i); 
	  }
	  myGUI.print("\n--|---------------------------------------------------");
	  myGUI.print("-------------------------------------------------------\n\t"); 
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print(Integer.toString(firstHop[i]));
	      myGUI.print("\t"); 
	  }

	  myGUI.println("\nLink Cost"); //or are they
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print("\t"+i); 
	  }
	  myGUI.print("\n--|---------------------------------------------------");
	  myGUI.print("-------------------------------------------------------\n\t"); 
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){
	      myGUI.print(Integer.toString(linkCosts[i]));
	      myGUI.print("\t"); 
	  }
 

	  //	  myGUI.println(""); 
  }

  //--------------------------------------------------
  public void updateLinkCost(int dest, int newcost) {

 
      linkCosts[dest] = newcost;


      // if we route directly, our new distance should be the cost of the direct route
      if(firstHop[dest] == dest)
	  {
	      distances[myID][dest] = linkCosts[dest];
	  }


      // if we route indirectly, then our distance to the node, is our routing nodes distance
      if(firstHop[dest] != RouterSimulator.INFINITY
	 && distances[myID][dest] != distances[firstHop[dest]][dest] + distances[myID][firstHop[dest]])
	  {
	      distances[myID][dest] = distances[firstHop[dest]][dest] + distances[myID][firstHop[dest]];
	  }

      // if the direct cost is cheaper than our indirect route, then
      // our new cost is the cost of the direct route.
      if(linkCosts[dest] < distances[myID][dest]){
	  distances[myID][dest] = linkCosts[dest];
	  firstHop[dest] = dest; 
      }
      
      // go through all destinations i via all indirect nodes dest. if
      // that produces a cheaper route, then route through that
      // inidrect node and update our routing
      for (int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
	  if (distances[myID][i] > distances[myID][dest] + distances[dest][i])
	      {
		  distances[myID][i] = distances[myID][dest] + distances[dest][i];
		  firstHop[i] = firstHop[dest]; 
	      }
      }





      //tell you're are're friendZ :OK_HANDSIGN:
      RouterPacket outgoing_pkt = new RouterPacket(myID, 0, distances[myID]);
      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {      
	  outgoing_pkt.destid = i;
	  if( linkCosts[i] != RouterSimulator.INFINITY && i != myID) {
	      sendUpdate(outgoing_pkt);
	  }
      }

      printDistanceTable();
  }

    //
    // public void broadcast(int distancevector[]){
    // 	RouterPacket outgoing_pkt = new RouterPacket(myID, 0, costs);
    // 	for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
    // 	    outgoing_pkt.destid = i;
    // 	    sendUpdate(outgoing_pkt);
    // 	}

    // }

}






















/*
                                       +++++:
                                     ++#@@@#++'
                                   .+@@@##@@@#++
                                  +#@+.,,,,.;@@#+'
                                 +#@.,,,,.,.,,,@@++
                               `#@;.,,,,,,,,,,,,'@@+,
                              '@@,,,,,,,,,,,,,,,,.@@+,
                             +@@.,,,,,,,,,,,,,,,,.,#@#;
                            +@@.,,,,,,,,,,,,,,,,,,,.;@#'
                          `+@@.,,,,,,,,,,,,,,,,,,,,,.:@#;
                          +@@,.,,,,,,,,,,,,,,,,,,,,,..@@#:
                        '+@@@.,.,,,,,,,,,,,,,,,,,,.@@@'@@#:
                       '+@@..+@',,,,,,,,,,,,,,,,,#@',..,,@#
                      '+@@,,,,.@@..,,,,,,,,,,,,.@@.,.,,,,.@+
                     '+@@..,..,.,@@.,,,,,,,,,,+@'.,,,,,.,,;@+
                     ++@.,.';..,..,#.,,,,,,,,,...,,,.'@@+.,@#`
                    ++@;..@@@@+.,,.,,,,,,,.,.,,,,,..@@@@@,.:@+
                   .++@..+@@@@@@.,,,,,,'.,.@.,,.,.,@@@@@@@.,@++
                   +++@..@@@@@@@@:.,,.:@.@,.@.,,,@@@@@@@@@.,@#+
                  ++++@..@@@@@@@@@@':+@,@@@..@@@@@@@@@@@@@:,##++
                  +++#@..@@@@@@@@@@@@@.@@@@@..@@@@@@@@@@@@:,+@++;
                 '+++@@,.@@@@@@@@@@@@.+@@'@@@..@@@@@@@@@@@.,;@+++
                 ++++@@,.@@@@@@@@@@@..@@+.:@@..:@@@@@@@@@@,,,@+++,
                ,++++@@.,@@@@@@@@@;,,,@@.,,@@.,..,;+++'+@@,,.@++++
                ++++#@,,.@:.,,,...,,,.,,,,,,..,,,.,,,,,,.##,,@#+++'
                ++++@@,.@.,,,,,,,,.,,,,.,,,.,.,,,..,.,,,,.::,+@++++
               +++++@.,,,.,,,,,,,..,,,,,,,,,,,,,.#@.,,,,,,,,,.@++++:
               ++++#@,,,,,,,,,,.#@,,.,.,,,,.,,,,,.@@..,,.,,,,,#@++++
              `++++@:,,,,,,,,,,@@,,,,,,,,...,,,,,..@@.,.,,,,,,.@++++
              +++++@.,,,,,,,,,@@,,,,,.+#.+@.,.,,,,,.@@,,,,,,,,.@#+++'
              +++++@,,,,,,,,.@@,,,,..+#.@..@+.,,,,,,.@#..,,,,,,@@++++
             ,++++#@.,,,,,,.@#..,..+#@.,@.,@:@,.,,,,,'@',,,,,.,@@++++
             +++++@@,,,.,,.#@,.,,+@,,@,,@..#.,@@,,,,,.@@,,,,,,,@@++++'
             +++++@@.,,,.,,@,,,.,:@..#.,#..#.,..@,,,,.@@@..,,,.@@+++++
            `+++++@@.,,.,.@@.,,@@,..,+''@@@@,,,.@#;,,.@@@@,.,,.@@+++++
            ++++++@@,.,..@@@,,#.@,.##@@@@@@@@@....;,,#@@@@#,,,#@@++++++
            ++++++@@@,,,#@@@@.@.+.#@@@@@@@@@@@@:.,@.'@@@@@@.,.@@@++++++
            +++++#@@@.,.@@@@@@@,'@@@@@@@@@@@@@@@,,@@@@@@@@@@.#@@@++++++
           '+++++@@@@@.@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+.@@@@@@@@#+++++:
           ++++++@@@@@@@@@'@@@@@@@@@@@@@@@@@@@@@@@@@@@@..,@@@@@@@#++++++
           ++++++@@@@@@@@..@@@@@@@@@..,.',...@@@@@@@@@@,,,,;@@@@@@++++++
          `++++++@@@@@@@.,,@@@@@@@.#,,..@,,..;.@@@@@@@@.,,,,@@@@@@++++++
          '+++++#@@@@@#.,,.@@@@@@.,@,;,,@.@..,,,,@@@@@',,.,,@@@@@@++++++`
          ++++++@@@@@@.,,,.@@@@'..,@###..;.@#+.',@@@@@.,,,,;@@@@@@++++++'
          ++++++@@@@@@.,,,.@@@@,.@+:,,,,,,,.,,@@.#@@@@.,,,,+@@@@@@+++++++
         .++++++@@@@@@.,,,.@@@@@#:..,,.,,,,,,,..@@@@@@,,,,,@@@@@@@+++++++
         +++++++@@@@@@.,,,.@@@@.,,,,,,,,,,,,,,,,,.@@@#,,,.,@@@@@@@+++++++
         +++++++@@@@@@,,,,,;@@.,.,,,,,,,,,,,,,,,,..++.,,,.,@@@@@@@+++++++
         ++++++#@@@@@@:,,,,,.,.,,,,,,,,,,,,,,,,,,.,,,,,,,,.@@@@@@@+++++++
         ++++++@@@@@@@;,,,,,,,,,,,,,,,,,,,,,,,,,,,,.,,,,,.,@@@@@@@#++++++'
        '++++++@@@@@@@;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,:@@@@@@@#+++++++
        +++++++@@@@@@@,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,:@@@@@@@#+++++++
        +++++++@@@@@@@..,,,,,,,,,,,,,,.....,,,,,,,,,,,,,,,:@@@@@@@#+++++++
        +++++++@@@@@@@..,,,,,,,,,,,.+@@@@@@@@@...,,,,,,,,,,@@@@@@@#+++++++
       .+++++++@@@@@@@.,,,,,,,,,.,;@@@@@@@@@@@@+,.,,,,,,,,.@@@@@@@#+++++++
       ;+++++++@@@@@@@.,,,,,,,,,,@@@@@@@@@@@@@@@@..,,,,,,,.@@@@@@@#+++++++
       ++++++++@@@@@@@@.,,,,,,,.@@@@@@@@@@@@@@@@@@#.,,,,,.:@@@@@@@++++++++
       +++++++#@@@@@@@@@.,,,,,'@@@@@@@@@@@@@@@@@@@@@..,,,,@@@@@@@@++++++++.
       +++++++#@@@@@@@@@@..,,@@@@@@@@@@@@@@@@@@@@@@@@+::#@@@@@@@@@++++++++:
      .+++++++#@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++;
      .+++++++#@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++;
      +++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++
      +++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@#+++++++++
      +++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++++
      +++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++++
     '++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++
     +++++++++++#@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++
     ++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++++++
     +++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@#++++++++++++'
    '+++++++++++++#@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++++++++
    +++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++++++:
   .++++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@#++++++++++++++++
   +``+++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++++++++++++
   `:+++++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++++++++++`
   +++++++++++++++++++#@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++++++++++++
  +++++++++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++++++++++++++
 +++++++++++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@#+++++++++++++++++++++++++
+++++++++++++++++++++++++@@@@@@@@@@@@@@@@@@@@@@@@@@@@@#+++++++++++++++++++++++'++:
+      '++++++++++++++++++#@@@@@@@@@@@@@@@@@@@@@@@@@@++++++++++++++++++++'       '
          +++++++++++++++++#@@@@@@@@@@@@@@@@@@@@@@@@+++++++++++++++++++.
            '+++++++++++++++#@@@@@@@@@@@@@@@@@@@@@#++++++++++++++++++`
              ;+++++++++++++++@@@@@@@@@@@@@@@@@@@++++++++++++++++++;
                :++++++++++++++@@@@@@@@@@@@@@@@#+++++++++++++++++'
                  '++++++++++++++@@@@@@@@@@@@#++++++++++++++++++
                    ++++++++++++++#@@@@@@@@#++++++++++++++++++
                      ;++++++++++++++#@@@@@@@@@+++++++++++++`
                        .++++++++++++++++####+++++++++++++
                           ;+++++++++++++++++++++++++++,
                              ;+++++++++++++++++++++;
                                 `,+++++++++++++,`
*/
