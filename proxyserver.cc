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
#define MAXDATASIZE 1000//might need to increase
#define LOCALDATASIZE 100000 
//#define port "8080"



using namespace std;


void listen_and_bind(struct addrinfo * servinfo, int & listen_socket, int & yes);
void childtasks(struct addrinfo hints, struct addrinfo *p, int new_socket);


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

  // char buf_server[MAXDATASIZE]; // message recieved
  // int numbytes; // size of message recieved


  //port = argv[1];			// set port number from command line

  char port[]{"8080"}; 		// the port we are using, will later be set on cli
  memset(&hints, 0, sizeof hints); // make sure hints is empty

  // NOTE TO SELF, ARE WE USING IPV4 ONLY? MIGHT HAVE TO CHANGE THIS
  hints.ai_family = AF_UNSPEC;	// both ipv6 and ipv4
  hints.ai_socktype = SOCK_STREAM; // using tcp socket
  hints.ai_flags = AI_PASSIVE;	 // use my ip.

  // call get addrinfo to fill upp servinfo with data from hints? ex
  // (www.addr.td, http or port, filled with info, result)


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
//
// Function child tasks
// Function that listens for and accepts messages
//

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
  //cerr << "numbytes from recv(): " << numbytes << endl; 
  //cerr << buf_server << endl; 
  //  copy(begin(buf_server),end(buf_server), ostream_iterator<char>(cout));//get req looking good
    
  /*
    Get the hostname from the get request
  */
  string get_host{buf_server};
  ////cerr << "Get host before filter" << get_host << endl;
  istringstream iss(get_host);
  iss.ignore(100, '\n');
  getline(iss, get_host);
  istringstream iss2(get_host);
  iss2.ignore(6, ' ');
  iss2 >> get_host;
  //cout << "hostname from get request: " << get_host << endl;

 

 /*
    TODO: Change connection: keep-alive to Connection: close
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
  //cerr << "getaddrinfo" << endl; 
  int rv;
  if ((rv = getaddrinfo( get_host.c_str(), "80", &hints, &inet_servinfo)) != 0) {
    fprintf(stderr, "internet getaddrinfo: %s\n", gai_strerror(rv));
    exit(1);
  }
    
  /*
    connect the client to the internet server
  */
  //cerr << "about to connect the client to the internet server" << endl; 

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
  //cerr << "Send the get request" << endl; 
    
  if (send(inet_sockfd, buf_server, numbytes, 0 ) == -1){
    perror("send"); 
  }

  /*
    recieve response
  */
  //cerr << "Recieve response " << endl;


  //this should be a recieve loop
  int totbytes{}; 
  char totbuf[LOCALDATASIZE];

  
  while( (numbytes = recv(inet_sockfd, buf_server, MAXDATASIZE-1, 0)) != 0)
    { 
      if( numbytes == -1)
	{
	  perror("recv");
	}
      //cerr << "totbytes"<< totbytes << endl; 
      //cerr << "numbytes"<< numbytes << endl;
    
      //cerr << endl << "Copy recieved message" << endl;

      copy(begin(buf_server),end(buf_server),begin(totbuf)+totbytes);    
      totbytes+=numbytes;
	
    }
  //cerr << "numbytes (shud be 0)" << numbytes << endl << endl; 

 
  // if ( (numbytes = recv(inet_sockfd, buf_server, MAXDATASIZE-1, 0 )) == -1)
  // {  
  // 	perror("recv"); 
  // }
  totbuf[totbytes] = '\0'; 
  // buf_server[numbytes] = '\0'; 
  //cerr << "after recv()" << endl;
   
  copy(begin(totbuf), begin(totbuf) + totbytes, ostream_iterator<char>(cout));//remote response
  cerr << "after copy" << endl << endl;
  //exit(1);

  /* 
     forward the response to our bowser
  */

  if (send(new_socket, totbuf, totbytes, 0 ) == -1){
    perror("send"); 
  }

}


