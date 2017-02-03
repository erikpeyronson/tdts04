#include <iostream>
#include <iterator>
#include <sstream>
#include <algorithm>

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/wait.h>
#include <signal.h>

#define BACKLOG 10
#define MAXDATASIZE 15000//might need to increase
#define LOCALDATASIZE 8000000




using namespace std;


void listen_and_bind(struct addrinfo * servinfo, int & listen_socket, int & yes);
void childtasks(struct addrinfo hints, struct addrinfo *p, int new_socket);
bool bad_words(string & data); //takes a data and a socket. for bad GETs, redirects a 302 to that socket. 


// Function for handling child processes
void sigchld_handler(int s)
{
  // waitpid() might overwrite errno, so we save and restore it:
  int saved_errno = errno;

  while(waitpid(-1, NULL, WNOHANG) > 0);

  errno = saved_errno;
}

// get sockaddr, IPv4 or IPv6:
void *get_in_addr(struct sockaddr *sa)
{
  if (sa->sa_family == AF_INET) {
    return &(((struct sockaddr_in*)sa)->sin_addr);
  }

  return &(((struct sockaddr_in6*)sa)->sin6_addr);
}

int main(int argc, char* argv[])
{
  int listen_socket, new_socket;
         
  struct addrinfo hints, *servinfo, *p;

  struct sockaddr_storage sockaddr_storage, their_addr; // addressinfo from the connecting part?
  socklen_t sin_size; // sizeoff() the structure used in accept, safe to ignore
  struct sigaction sa; // process handler
  int yes=1;
  char s[INET6_ADDRSTRLEN]; // lenght of ip address
  int rv; // return value. error codes?


  if(argc < 2){
    cerr << "Usage: ./proxyserver PORT (Example: ./proxyserver 8080)\n";
  }
  string portstring = argv[1];
  char port[87];
  copy(portstring.begin(), portstring.end(), begin(port));
  port[portstring.size()] = '\0';
  
  memset(&hints, 0, sizeof hints); // make sure hints is empty

  hints.ai_family = AF_UNSPEC;	// both ipv6 and ipv4
  hints.ai_socktype = SOCK_STREAM; // using tcp socket
  hints.ai_flags = AI_PASSIVE;	 // use my ip.

  if ((rv = getaddrinfo(NULL, port, &hints, &servinfo)) != 0)
    {
      fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rv));
      return 1;
    }

  listen_and_bind(servinfo, listen_socket, yes); 
  sa.sa_handler = sigchld_handler; //zombie handling
  sigemptyset(&sa.sa_mask); 
  sa.sa_flags = SA_RESTART; 

  if( sigaction(SIGCHLD, &sa, NULL) ==  -1 ){
    perror("sigaction"); 
    exit(1); 
  }

  printf("server: waiting for connections...\n"); 

  while(true) { //accept loop FORK in the loop
    sin_size = sizeof their_addr;
    new_socket = accept(listen_socket, (struct sockaddr *)&their_addr, &sin_size); 
    if(new_socket == -1) { // no one to talk to 
      perror("accept"); 
      continue; 
    }

    //network to printable (human readable) 
    inet_ntop(their_addr.ss_family, 
	      get_in_addr((struct sockaddr *)&their_addr),
	      s, sizeof s); 
    printf("server: got connection from %s\n", s); 



    if(!fork()) 
      { 
	close(listen_socket);//child doesn't need the listener

	childtasks(hints, p, new_socket); 
	close(new_socket); 
	exit(0);
      } //end of fork

    close(new_socket); //parent doesn't need the child's socket
  }

  return 0;
}


//
// function listen_and_bind
// listens for connections on port, opens binds to socket
//


void listen_and_bind(struct addrinfo * servinfo, int & listen_socket, int & yes)
{
  struct addrinfo *p;

  for( p = servinfo; p!= NULL; p = p -> ai_next)
    {    
      if((listen_socket = socket(p -> ai_family, p -> ai_socktype, 
				 p->ai_protocol)) == -1) // get file descriptor for p
	{
	  perror("server: socket"); // print error if socket not found
	  continue;
	}
      if (setsockopt(listen_socket, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(int)) == -1) //set options for socket
	{
	  perror("setsockopt");
	  exit(1);
        }

      if (bind(listen_socket, p->ai_addr, p->ai_addrlen) == -1) // bind the socket to the port 
	{
	  close(listen_socket);
	  perror("server: bind");
	  continue;
        }

      break;
    }

  freeaddrinfo(servinfo); 

  if(p == NULL) {
    fprintf(stderr, " server: failed to bind\n");
    exit(1); 
  }

  if(listen(listen_socket, BACKLOG) == -1) { //wait for a connector
    perror("listen");
    exit(1); 
  }

  return; 
}

/*
  This function does the following
  1. Recieve a get request from the browser
  2. Search that get request for bad words (reirect if neccessary)
  3. Modifies the get request (keep-alive changed to close etc)
  4. Connects (our) client to a web server
  5. Sends our get request
  6. (our) server recieves a response
  7. Filter that response for bad words
  8. Deliver a good response to the web browser  
 */
void childtasks(struct addrinfo hints, struct addrinfo *p, int new_socket){
  int inet_sockfd;
  char buf_server[MAXDATASIZE]; // message recieved
  int numbytes; // size of message recieved
  struct addrinfo *inet_servinfo; //addrinfo of the remote host

  if ((numbytes = recv(new_socket, buf_server, MAXDATASIZE-1, 0)) == -1) {
    perror("recv");
    exit(1);
  }
  buf_server[numbytes] = '\0';

  /*
    Filter the get request
  */
  string get_request{buf_server}; 
  if(bad_words(get_request)){
   
   if (send(new_socket, 
  	       "HTTP/1.1 302 Found\r\nLocation: http://www.ida.liu.se/~TDTS04/labs/2011/ass2/error1.html\r\n\r\n",
  	       89, 0 ) == -1){
  	perror("302"); 
   }
   exit(1);
  }
  
  

  /*
    Get the hostname from the get request
  */
  string get_host{buf_server};
  istringstream iss(get_host);
  iss.ignore(100, '\n');
  getline(iss, get_host);
  istringstream iss2(get_host);
  iss2.ignore(6, ' ');
  iss2 >> get_host;



  

 

  /*
    Change connection: keep-alive to Connection: close
  */
  string buf_server_string{buf_server};
  auto pos = buf_server_string.find("Connection:"); // will give position of first char of keep_alive
  
  buf_server_string.replace(pos+12, 10, "close");

  memset(&buf_server, 0, sizeof buf_server);
  copy(begin(buf_server_string),end(buf_server_string), begin(buf_server));
  buf_server[buf_server_string.size()] = '\0';


  /*
    Make an addrinfo from the hostname
    Note: do we want to use another port than 80? 
  */
  int rv;
  if ((rv = getaddrinfo( get_host.c_str(), "80", &hints, &inet_servinfo)) != 0) {
    fprintf(stderr, "internet getaddrinfo: %s\n", gai_strerror(rv));
    exit(1);
  }
    
  /*
    connect the client to the internet server
  */
  for(p = inet_servinfo; p != NULL; p = p->ai_next) {
    if ((inet_sockfd = socket(p->ai_family, p->ai_socktype,
			      p->ai_protocol)) == -1) {
      perror("client to inet: socket");
      continue;
    }
    if (connect(inet_sockfd, p->ai_addr, p->ai_addrlen) == -1) {
      close(inet_sockfd);
      perror("client to inet: connect");
      continue;
    }
    break;
  }
  if (p == NULL) {
    fprintf(stderr, "client to inet: failed to connect\n");
    exit(2);
  }

  /*
    send the get request
  */  
  if (send(inet_sockfd, buf_server, numbytes, 0 ) == -1){
    perror("send"); 
  }

  /*
    Recieve response
  */
  int totbytes{}; 
  char totbuf[LOCALDATASIZE];

  
  while( (numbytes = recv(inet_sockfd, buf_server, MAXDATASIZE-1, 0)) != 0)
    { 
      if( numbytes == -1)
	{
	  perror("recv");
	}
      if(numbytes + totbytes >= LOCALDATASIZE){
	cerr << "Too big recieve, dude" << endl;
	break;
      }
      copy(begin(buf_server),end(buf_server),begin(totbuf)+totbytes);    
      totbytes+=numbytes;
    }

  totbuf[totbytes] = '\0'; 

  /*
    Filter the response
   */
  string message{totbuf}; // (totbuf, totbytes); 
  if(bad_words(message)){    
   if (send(new_socket, 
	       "HTTP/1.1 302 Found\r\nLocation: http://www.ida.liu.se/~TDTS04/labs/2011/ass2/error2.html\r\n\r\n",
	       89, 0 ) == -1){
	perror("302");
      }
   cerr << "GAYY";
    exit(0); 
  }

  /* 
     Forward the response to our bowser
  */
  if (send(new_socket, totbuf, totbytes, 0 ) == -1){
    perror("send"); 
  }

}



bool bad_words(string & data){
  transform(begin(data), end(data), begin(data), ::tolower); 

  string header{}; 
  copy(begin(data), begin(data) + data.find("\r\n\r\n"), begin(header)); 
  

  /*
    might need to remove blank spaces around here fam
  */

  if( header.find("content-encoding: gzip") != string::npos ||
      header.find("text/html") == string::npos)
    {
      return false;
    }

  if(  data.find("spongebob") != string::npos ||
       data.find("paris hilton") != string::npos ||
       data.find("britney spears") != string::npos ||
       data.find("norrk%c3%b6ping") != string::npos||
       data.find("norrköping") != string::npos||
       data.find("norrkoping") != string::npos)
    
    {
  
      return true; 
      
    }
  return false; 
}






















/*
............DN............Z7........................................................................
............NI7...........?,I$..........................=$Z7??88....................................
............DZ8M,.........:,,,I.......................~NZI????????..................................
............=MMMD.......=M+=,,,,:....................?$??$$=??I??I?I................................
..............MMO8.....,DI+=,,,,?:..................?M?O?N?+OZ7N?I??................................
.............,+=I$...,N$+I++~,,,:+..................88$87?IO$Z8M$D7?7...............................
..Z+?.........MMM877II+?I$7I++Z?,:=.................7MMMM$778D8$MMZN??..............................
..?:~I+=....~?MM~~?I7?II$$7I77:7,,O...............I$NNZ8MMOOMMMMMMMM?7..............................
..,,+NM8II$7II8MI:+???+?ZZODI?=I,,8=..............8NMN7$77ZZI7ZZ$NMD??..............................
..,,IMMDO877I?$MM8+?II?+7OZ7D8II,,Z~..............NMODM$778DOZZOMMODZOO.............................
..~,IMNNOD7III7OMM:+II77DDDMDDZ+,,Z~..............MNZM8$7ZNNNNZZMMNMZNN.............................
..I,:ZMD8OIIIIII$M7+DMM$.....=MD,~8..............:M8ZMNZ$$NNDOZZNMMMMMO.............................
..=D:~MZ$OZZI7I?+$OM?+$M........$I..................7OMMM??I7ZOMMMMMM$..............................
....I+7$Z8O8OO??D:.:MMMMM~............................7NMMNMMMMMMMMZ................................
....~,:+8ZO8ODDD....7MZMMI............................DDO8ZZNMNZ8MNMMMMD,...........................
.....?,,:?Z7$M........MM=7....................IMMNDM7$MD$$$ZZOZZMMMMNMMMDNZNMMDODNN$................
......+,,:+$N.........7MOO=.................:8DNM8ZN7ZZ$Z7OZMOZZ8NMZ8O$IMM8O$Z$$Z$ZNMN..............
.......+,,:~7..........D+=~?.,...........ZN88DDZMM8OZZ$7DZ777ZZZNMNMD8ODMMM8Z$777ZZOMD8=............
..........I78.........8=?Z8OOO=.......=M7?7$NMMDMMM8MOI??7==?I$D77MMMDMMZZMM88Z=~+7ZZ8Z?:...........
......................7Z$I7778~......:Z+=IZ8ZZ8M7777$MI77I~=?7MMO$Z$$MNZZZZZNNN7=~IZZZZ7D...........
.....................?O7I7$77$?.....,M+=Z$ZZ$7I$OMNDDN8ZZ$~+7ZDDMMMOMM?7$ZZZZZN8$777Z$ZZNZ..........
.....................,MMI?Z$8MDO....7O?7Z7?=????M8MMZZMMZZMIMOION8ZM+~~?ZZZZZZMMZZ$Z$$ZZM87?8.......
......................8$8$$$IZMM....=D7Z$7=~~+??$N7$I7DNMMMMN7I777MI~:I7$ZZZZZNMNN8OZZZZ8MZZ?$......
......................DZ787$8MMM.....O88$7+~~=??7ZM$777$77O777I77OI==?I7$ZZZZZMM8Z$$ZZZZ$7ZZ$7=.....
......................=OO8O8MM7+7.$M7?7$M$?+=~??7$M8I7NMMM8M77$$MN+?77$$ZZZZONMMO7=~?77$+IZZZZO$....
.......................IONND$M7I$~8I~++IDMZ$8NOIZ$M8$NMMMMMNMM7ZM877$8D8ZZZOMMMM8?~~~=+==7ZZZZ8M....
........................NDMN$88?IDI=~~+?Z8MMN8OOODMNMMNM$8MNND7$MMZ8DDMMMMMMM8DMMM=:=+=~~?ZZZZ$M....
........................MMMMZ$NM+ID7??I7ZZMMMMZZZZM88MMMMMMMMM$7MZZ7$ZZZ8MDIZZZZMMMMMM$Z777ZZZZ$=...
........................8DMMN8MM8?OMN8$$ZZMMMDZ$Z88O7ZZMMMMM$7$ZNI7III$ZMMMMOZZ8N,..ZN8NDOZOZZZ7=...
..........................DNMMN8M7IZZZNOO8MMOZI7DMO$7$7$ZO8O$$$$DM$$ZZ7O88MO88NM....INZ7==?$ZZMZZ...
...........................OMMNO7M+?D8Z8MMO8MMNM7$77ZZDMNMMM8O77IZNMZ$Z8MNMDDNN:..N7I7ZZZZZZMMMMM:..
............................MM$$7$$+?DZZMMMMMMMN$7$Z$Z8????IMM$77$O8MM8DNMMMMD...$7$8ZZ8Z88MMMDNM7..
............................MMZOZ$MD?IOZMMD?MMMMMM87N8?==?++7OMO7$$OMMMMMMMM~...=N8ZZZZZZZZ8N8MM....
............................=MMMMMIM$I8NM7..+MMMMMMM$77$ZO8OZZDMMM8MMMMMMMMM=Z8ZII?I$ZO8ZZO8M8......
..............................DMMMNO8?$D,....:MMMMMMMDZZI78DN88DNMMMMMMMMMMM$?=?7$ZZZZ8NMMMM........
................................:DMMMM?I=.....?M77ZZI7?+???++7$ZZZMMMMMMMMD7??$ZZZZZNM8DMZ..........
.....................................,8?O7....MM7?7OMN$ZZZ88OZZO8NMMMMMM8DOZZN88MMNN8?..............
......................................O7?Z...=NN8D8DDNM88NNNMMNNMMDN8ONMOOZZZ8MMMO..................
.......................................DI7?.~M$$$Z$7$ONNOZ$$Z$$ZZ$$$8$MMDOOZZON,....................
........................................DDZ$8N87MODD$NM$$$88O7M$$O$ZNMONNOZ8MD......................
........................................OM8DMO7$MMMNOM8Z8ZN8O78MNO$$$ZNMMMNM........................
..........................................MMMMMMOO$$$DNMMNDZ$7$$88NMMMMMMMMN........................
..........................................=MMMMMNNMMD$ZONMMN8ZZOMMMMMMMMMMM.........................
..........................................:MMMMMNMNDNNMMMMNOOMMMMMMMMMMNZMD.........................
..........................................Z7MNMMMMMDNNDNNNMNNMMMMMMMD7D$Z7M~........................
........................................$88M=+?7MMMMMDDNNNMMMMMMMMZ=+??O$ZIN........................
........................................ZINI?II77ZMMMMMMNNMNMMMMNDI?~?7?ZZ7?7.......................
.......................................877$?7ID=?IIOMMMMMMNMMMD$7787+?7777ZI8=......................
......................................N$78$ZDO~~777?ZMMMMMMMMM$7II$ZZ7Z7Z?Z$=8......................
......................................8+$8ZZZ+~?7$I?I78MMMMMMMZ$7I?$ZOZ$I?ZZ?I......................
.....................................,7+7DZ7=~?7Z$?+$Z$DMMMMNM8Z$I+?$ZOD~=$ZI?:.....................
.....................................Z7=?OOI~:I7Z$??8$7Z?...NI8M$7???IZN7I$Z7II.....................
.....................................Z7??7NI~~77ZZ?DZZ7N....NN$D$7??~+7ZZ?7$$77.....................
.....................................D$7?$O?~~7ZZ$IDZZMD....:M88$7??~=7$7I$$Z7M,....................
.....................................$7I7Z7?~=7$ZZZNMM~.......NMD$??~~77$$$ZZ7M,....................
.....................................~$$7Z$?+?7ZZZ8MMO.........:MOI?~=7$ZZZZZ$M.....................
......................................Z7$ZZ$?I7ZZZMM=...........D87I=?$ZZZZZZN8.....................
......................................DD$ZZ$$$ZZZZMM.............O$7?7ZZZZM88M:.....................
......................................,MMOZZ$$ZZZZMO.............7Z$$ZZZZOMMMM......................
........................................M8ZOZZZZOMM?.............,NZ$ZZZ$8ZNMM......................
........................................MOZOZ88DMMN...............DMD8O8N8ZZMN......................
.......................................=87$ZMMMMM87................8MMMMO$ZZ8M......................
.......................................O7+7$8MMMZN:.................MMMMI+$ZZ8=.....................
......................................ND?+$Z77ZZ8M..................:MMM8?+7ZMNN....................
.....................................:O8NZZZDNZ7N.....................MMMMOZOMNNM=..................
.....................................?OZNZZOMD78.......................ZNN8ZMM88Z8,.................
.....................................$$ZMDZZZ$M=........................DMO8MMZ$OZOM................
....................................ZZZ8MNZZZ8M=........................8NMZ7ZZ$MZZ88...............
....................................$?77O8ZZNNMM........................ZZMD$ZZZM888M$..............
...................................?77D7$ZZZ8ZMM........................$ZZ8ZZZ8DZMMNM..............
................................,Z8$7$777$ZZOOMM........................M8ZOMNOZ8MZDNM~.............
................................8O?=????IZZ8OO8?Z7......................MMZZ77777I77~~MN............
................................DM+??????N++OOI7$8......................M$+++=++?++?I?OZ............
................................I$ZDOOOZMOI?OM78N+.....................?MM?I??+?8?+?NZMD............
.................................IMMNMMMMNMDMDMM$N.....................~8ZDOD7IDOMO7MMMD............
..................................=MMMMMMNNMM$$..........................~MMMMMNMMMMMM8.............
...................................DMMMMDDMMND..............................MMMDNMNMMMZ.............
...................................DMMN8MMMMM8..............................MMMMN8MMMM7.............
...................................DMMMMNMMMI................................IMMNMDNMM~.............
...................................,MMMMNNMM..................................MMMMMNMN..............
....................................MMMMMMMD..................................,MMMMMMM..............
....................................NMMMDMMM...................................NNNNDMM..............
....................................8MNNNMMM..................................~MMMMMMM:.............
....................................NMMMNMMM?.................................NMMMNNMMD.............
..................................MMMMMMNNNDM~................................MMMMMMMNMM7...........
...............................?MMMMMMMNMMMMM?................................MMMMMMMMMMO$..........
............................OMMMMMMMMMMMMMMMN.................................MMMMNNMMMMD8..........
.........................7MMMMMMMMMMMM$=.......................................7MMMM8Z7I............
.........................:MMMMMMMD=.................................................................
....................................................................................................
*/
