#include <iostream>
#include <iterator>
#include <sstream>

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
//#define port "8080"



using namespace std;


void listen_and_bind(struct addrinfo * servinfo, int & listen_socket, int & yes);

void childtasks(struct addrinfo hints, struct addrinfo *p, int new_socket);
// making a socket() system call returns a socket descriptor
// send() recv() are used for communication
/*
  Stream Socket = SOCK_STREAM
  HTTP uses this stream sent will arrive in the same order as sent TCP


  Datagram Sockets  SOCK_DGRAM

  connectionless may be out of order

  # The needed structs
 
  struct addrinfo 
  {
  int              ai_flags;     // AI_PASSIVE, AI_CANONNAME, etc.
  int              ai_family;    // AF_INET, AF_INET6, AF_UNSPEC
  int              ai_socktype;  // SOCK_STREAM, SOCK_DGRAM
  int              ai_protocol;  // use 0 for "any"
  size_t           ai_addrlen;   // size of ai_addr in bytes
  struct sockaddr *ai_addr;      // struct sockaddr_in or _in6
  char            *ai_canonname; // full canonical hostname

  struct addrinfo *ai_next;      // linked list, next node
  };

  Usage : load up the struct with values, call getaddrinfo() which gives a pointer
  to a linked list of these structs




  struct sockaddr {
  unsigned short    sa_family;    // address family, AF_xxx
  char              sa_data[14];  // 14 bytes of protocol address
  };

  struct sockaddr_in {
  short int          sin_family;  // Address family, AF_INET
  unsigned short int sin_port;    // Port number
  struct in_addr     sin_addr;    // Internet address
  unsigned char      sin_zero[8]; // Same size as struct sockaddr
  };

  Usage: create your own struct sockaddr_in (in=internet) that is used for ipv4
  IMPORTANT sockaddr_in and socaddr can be cast to eachother aka connect can be called
  with our own sockaddr_in casted last minute


  // (IPv4 only--see struct in6_addr for IPv6)

  // Internet address (a structure for historical reasons)
  struct in_addr {
  uint32_t s_addr; // that's a 32-bit int (4 bytes)
  };

  usage: the member sin_addr in sockaddr is of this type. Used to store the ip


  FUNCTION inet_pton() converts and ip in dot format to inet_addr
  EXAMPLE inet_pton(AF_INET, "10.12.110.57", &(sa.sin_addr)); // IPv4

  FUNCTION inet_ntop converts an inet_addr to dot format
  inet_ntop(AF_INET, &(sa.sin_addr), ip4, INET_ADDRSTRLEN);


  ### SYSTEM CALLS

  # getaddrinfo() - sets up the structs you need later on

  int getaddrinfo(const char *node,     // e.g. "www.example.com" or IP
  const char *service,  // e.g. "http" or port number
  const struct addrinfo *hints,
  struct addrinfo **res);

  FUNCTION gai_strerror() prints the error message from getaddrinfo



  # socket() - get the file descriptor

  int socket(int domain, int type, int protocol); 

  domain
  type = what type of socket
  int protocol = what protocol can be filled with getprotobyname() "tc" or "udp"

  you feed getaddrinfo() into socket()



  # bind() - associate the socket with a port on the local machine unessecary in clients

  int bind(int sockfd, struct sockaddr *my_addr, int addrlen);

  sockfd = socket file descriptor returned by socket()


  # connect() - connect to a remote host

  int connect(int sockfd, struct sockaddr *serv_addr, int addrlen);
  sockfd = socket file descriptor that you got from socket()


  # listen() wait for incomming connection.

  listen() then accept() 

  bind() is called first then listen() then accept()

  # accept() you accept() the calls on the listen() port which someone tries to connect() to

  int accept(int sockfd, struct sockaddr *addr, socklen_t *addrlen); 


  send() recv() 

  int send(int sockfd, const void *msg, int len, int flags); 


*/



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

  // hints = template, *servinfo = info about server, *p = loop condition to traverse servinfo
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



    if(!fork()) { //this is the child process
      // is close(listen_socket) global?????
      //      close(listen_socket);//child doesn't need the listener

      /*
	rather than sending a string back to the port connect to the web server
	send forward the http message
	listen on same port
	send content back to browser
      */
      childtasks(hints, p, new_socket); 
 

      close(new_socket); 
      exit(0);
    } //end of fork
     

    close(new_socket); //parent doesn't need the child's socket
  }



  return 0;
}





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
      cerr << "numbytes from recv(): " << numbytes << endl; 
      cerr << buf_server << endl; 
      //  copy(begin(buf_server),end(buf_server), ostream_iterator<char>(cout));//get req looking good
    
    


      /*
	Get the hostname from the get request
      */
      string get_host{buf_server};
      //cerr << "Get host before filter" << get_host << endl;
      istringstream iss(get_host);
      iss.ignore(100, '\n');
      getline(iss, get_host);
      istringstream iss2(get_host);
      iss2.ignore(6, ' ');
      iss2 >> get_host;
      //cout << "hostname from get request: " << get_host << endl;


      /*
	Make an addrinfo from the hostname
	Note: do we want to use another port than 80? 
      */



      // struct hostent  *hs;
      // hs = gethostbyname( get_host.c_str());
      // // if( hs = gethostbyname( get_host.c_str()) == NULL)
      // if( hs == NULL)
      //   {
      // 	herror("gethostbyname"); 
      // 	return 2; 
      //   }
      
      //we are here how tf do u get an ip from hs? xDDDD
      // cerr << hs->h_addr_list[0] -> s_addr << endl;
      // cerr << hs->h_addr << endl;
      cerr << "getaddrinfo" << endl; 
      int rv;
      if ((rv = getaddrinfo( get_host.c_str(), "80", &hints, &inet_servinfo)) != 0) {
	fprintf(stderr, "internet getaddrinfo: %s\n", gai_strerror(rv));
	exit(1);
      }
    
      /*
	connect the client to the internet server
      */
      cerr << "about to connect the client to the internet server" << endl; 

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
      cerr << "Send the get request" << endl; 
    
      if (send(inet_sockfd, buf_server, numbytes, 0 ) == -1){
	perror("send"); 
      }

      /*
	recieve response
      */
      cerr << "Recieve response " << endl;

      if ( (numbytes = recv(inet_sockfd, buf_server, MAXDATASIZE-1, 0 )) == -1){  
	perror("recv"); 
      }

      buf_server[numbytes] = '\0'; 
      cerr << "after recv()" << endl;
   
      copy(begin(buf_server), begin(buf_server) + numbytes, ostream_iterator<char>(cout));//remote response
      cerr << "after copy" << endl;

      /*
	forward the response to our bowser
      */

      if (send(new_socket, buf_server, numbytes, 0 ) == -1){
	perror("send"); 
      }
}


