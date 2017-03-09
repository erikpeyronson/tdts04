import javax.swing.*; 

/*
  Known problems include that 
 */       

public class RouterNode {
  private int myID;
  private GuiTextArea myGUI;
  private RouterSimulator sim;
  private int[] costs = new int[RouterSimulator.NUM_NODES];


    /*

     */
    private int[][] distances = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES]; 

    //
    private int[] linkCosts = new int[RouterSimulator.NUM_NODES];
    private int[] firstHop = new int[RouterSimulator.NUM_NODES];


    // change this to false to reverse poisonreverse
    public static final boolean poisonreverse = true; //!!false; //!!!!!!!true; 
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
      //keep track of if we need to update
      boolean is_changed = false;
      myGUI.println("from: " + Integer.toString(pkt.sourceid) + " to: "+ Integer.toString(pkt.destid)); 

      // Enter the sent distance vector into our matrix. 
      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i){   
	    if(distances[pkt.sourceid][i] != pkt.mincost[i])
	      {
		  is_changed = true; 

		  distances[pkt.sourceid][i] = pkt.mincost[i];
	      }

      }


      if(is_changed){ //did anything change? 

	  // if our distance vector changes, we have to rebroadcast
	  // it, so remember wether or not it did
	  is_changed = false; 

	  for(int des = 0; des < RouterSimulator.NUM_NODES; ++des){   


	      // for every entry in the distance matrix, if our
	      // distance to a target node Indes is greater than a
	      // node des distance to indes + our distance to des, our
	      // distance to indes is our distance to des + des to
	      // indes

	      // if us → indes is longer than us → des → indes,
	      // reroute through des
	      for(int indes = 0; indes < RouterSimulator.NUM_NODES; ++indes){
		  if( distances[myID][indes] > distances[myID][des] + distances[des][indes] 
		      && linkCosts[des] != RouterSimulator.INFINITY)
		      {
			  is_changed = true; 

			  distances[myID][indes] = distances[myID][des] + distances[des][indes];
			  firstHop[indes] = des;
		      }
	      }

	      
	      if ( des != myID ){
		  //note to self: this doesnt rip apart poisonreverse as this doesn't affect other nodes infinitycosts
		  if(linkCosts[des] < distances[myID][des]){ 

			  is_changed = true; 

			  distances[myID][des] = linkCosts[des]; 
			  firstHop[des] = des; 

		  }
		  
		  if (linkCosts[des] != RouterSimulator.INFINITY 
		      && distances[myID][des] !=  distances[myID][firstHop[des]] + distances[firstHop[des]][des])
		      {
			  is_changed = true;

			  distances[myID][des] = distances[myID][firstHop[des]] + distances[firstHop[des]][des];
		      }

	      }
	  
	  }


   
	  //if anything changed, tell our neighbours what is up
	  if(is_changed) {

	      int fakenews[] = new int[RouterSimulator.NUM_NODES]; 
	      for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
		  for(int j = 0; j < RouterSimulator.NUM_NODES; ++j) {
		      //without poisonreverse, this is an arraycopy
		      if(firstHop[j] == i && poisonreverse){ 
			  fakenews[j] = RouterSimulator.INFINITY; 
		      }
		      else{
			  fakenews[j] = distances[myID][j]; 
		      }
		  }
		  if( linkCosts[i] != RouterSimulator.INFINITY && i != myID) {
		      sendUpdate(new RouterPacket(myID, i, fakenews));
		  
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
	      if(//linkCosts[i] != RouterSimulator.INFINITY
		 //&& linkCosts[i] != 0
		 true
		 ){
		  myGUI.print(i +"|\t"); 
		  for(int j = 0; j < RouterSimulator.NUM_NODES; ++j){

		      myGUI.print(Integer.toString(distances[j][i])); 
		      myGUI.print("\t"); 
		  }
		  myGUI.println(""); 
	      }
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
	  if (distances[myID][i] > distances[myID][dest] + distances[dest][i]
	      && linkCosts[i] != RouterSimulator.INFINITY)
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
