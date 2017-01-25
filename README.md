# CNProject
This is a chat application with client server architecture where one client can do the following functions:
1. Broadcast message- Message sent from one client will be sent to all other clients
2. Broadcast file- File sent from one client will be sent to all other clients with each client creating a directory and storing the files in it.
3. Unicast message- Message sent from one client will be sent to only the specified destination client
4.Unicast file- File sent from one client will be sent to only the specified destination client
5. Blockcast message- Message sent from one client will be sent to all other clients except the client which is specified here to get blocked.
6. Blockcast file- File sent from one client will be sent all other clients except the client which is specified here to get blocked.


Steps to start the Server 


1. Open Command Prompt
2. Navigate to CNProject->out->artifacts->server_jar folder
3. Run the jar CNProject.jar by typing “java -jar CNProject.jar 1234”   (instead of 1234 any port from 0         to 65536) can be given
4.Server should successfully start


Steps to start the Client

1.Open Command Prompt
2.Navigate to CNProject->out->artifacts->client_jar folder
3.Run the jar CNProject.jar by typing 
“java -jar CNProject.jar #ServerIPAddress #serverPort #clientName”   

Note:If running on the same localhost #ServerIPAddress is 127.0.0.1 and #serverPort is the port used to start the server
4.Run multiple instances of this to create multiple clients

Command to send Message or File
1. Unicast Message-  	 unicast <to-ClientName> <Message to be sent>
2. Unicast File-             	unicast_file <to-CLientName> <File Location>
3. Broadcast message- 	broadcast <Message to be sent>
4. Broadcast File-		broadcast_file <FileLocation>
5. Blockcast message-	blockcast <blocked_ClientName> <Message to be sent>
6. Blockcast_file-		blockcast_file <blocked_ClientName> <File Location>
 

Design choices:
Server maintains two threads for each client one thread for reading into the socket, other to write into the client's socket. Any processing like unicast, broadcast request of a client is executed in its reading thread.
Client also has two threads associated with the socket one for reading from the socket, the other write onto the socket. Client also has a thread that reads from console for commands. Every download(receive file) and upload(send file) use a pool of IO threads using ExcecutorService.
Both the server and the client are completely asynchronous. While the client is sending/recieving it is free to do other tasks. The asynchronous updates of sending/receiving are periodically published to the console.
