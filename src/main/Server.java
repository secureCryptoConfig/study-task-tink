package main;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.aead.AeadFactory;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.google.crypto.tink.config.TinkConfig;
import com.google.crypto.tink.signature.PublicKeyVerifyFactory;

import main.Message.MessageType;

/**
 * Class that simulates the behavior of a stock-server that processes client
 * requests.
 * 
 * Server retrieves buy/sell orders for stock from clients. Clients can be
 * registered from the server with their public key. This public key is needed
 * to be able to check the validity of all following signed messages that the
 * client sends. The validity of signed messages will be checked and valid
 * incoming orders will be stored encrypted such that no unauthorized party can
 * see unencrypted order.
 */
public class Server extends Thread {
	// Queue to store orders of a client with a specific ID
	HashedMap<Integer, CircularFifoQueue<byte[]>> queues = new HashedMap<Integer, CircularFifoQueue<byte[]>>();
	// maximum timeout of server used in "run" Method
	private static int sendFrequency = 5000;

	// Key for encrypt orders before storing. Gets initialized with the first run of
	// AppMain.java
	static KeysetHandle masterKey;

	// all registered clients with their Keys
	List<KeysetHandle> clients = Collections.synchronizedList(new ArrayList<KeysetHandle>());

	/**
	 * Server retrieves key for later signature validation from client
	 * 
	 * @param key publicKey of client
	 * @return int : client ID
	 */
	public synchronized int registerClient(KeysetHandle key) {

		if (clients.indexOf(key) == -1) {
			clients.add(key);
		}

		int id = clients.indexOf(key);

		// new Queue of the client to store his later incoming orders
		queues.put(id, new CircularFifoQueue<byte[]>(100));
		return id;
	}

	/**
	 * Method to check signature validation of a incoming message.
	 * 
	 * @param clientID
	 * @param order
	 * @param signature
	 * @return boolean resultValidation: shows if signature was valid
	 * @throws CoseException
	 */
	private boolean checkSignature(int clientID, byte[] order, byte[] signature) {

		// Key of client. This key is used for signature validation
		KeysetHandle key = clients.get(clientID);
		// store result of the validation. Default : false
		boolean resultValidation = false;

		// TODO Perform validation of the given "signature" with
		// the already defined "key" of the client. 
		//Store/Return the result in 'resultValidation' that shows if the validation was (not) successful.
		
		return resultValidation;

	}

	/**
	 * Method for symmetric encrypting incoming order of client
	 * 
	 * @param clientId
	 * @param order    order send by client
	 * @return boolean : shows if encryption could be done successfully
	 * @throws CoseException
	 */
	private boolean saveOrderEncrypted(byte[] order, int clientId) {

		byte[] encryptedOrder = null;
		KeysetHandle key = masterKey;

		// TODO Perform a symmetric encryption of the given "order" with the already
		// defined "key". Store the chiphertext in the already defined variable
		// "encryptedOrder"

		

		// Add encrypted order in queue of client
		return queues.get(clientId).add(encryptedOrder);

	}

	/**
	 * Method for decrypting stored encrypted order if clients requests his already
	 * send orders
	 * 
	 * @param encryptedOrder encrypted order
	 * @return String : plaintext of decryptet order
	 * @throws CoseException
	 */
	private String decryptOrder(byte[] encryptedOrder) {
		KeysetHandle key = masterKey;
		String decryptedOrder = null;

		// TODO Perform a symmetric decryption of the given "encryptedOrder" with the
		// already defined "key". Store/Return the plaintext in the already defined String
		// variable "decryptedOrder"
		
		return null;

	}

	/**
	 * Generation of key for later encryption of orders
	 * 
	 * @return byte[] key bytes
	 */
	public static KeysetHandle generateKey() {
		try {
			TinkConfig.register();
			return KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Message get processed depending on its MessageType and validation result.
	 * 
	 * @param type             BUY/SELL stock or GETORDERS
	 * @param clientId
	 * @param isCorrectMessage shows if message signature was correct
	 * @param signedMessage    message sent from the client to server
	 * @return
	 * @throws CoseException
	 * @throws JsonProcessingException
	 */
	private String parseMessage(MessageType type, int clientId, boolean isCorrectMessage, SignedMessage signedMessage)
			throws JsonProcessingException {
		switch (type) {
		case GetOrders:
			CircularFifoQueue<byte[]> q = queues.get(clientId);
			String answer = "";
			for (int i = 0; i < q.size(); i++) {
				byte[] encryptedOrder = q.get(i);
				String decrypted = "";
				decrypted = decryptOrder(encryptedOrder);
				answer = answer + Message.createServerSendOrdersMessage(decrypted) + "\n";
			}
			return answer;
		case BuyStock:
		case SellStock:
			boolean encryptionResult = saveOrderEncrypted(signedMessage.getContent().getBytes(), clientId);
			if (encryptionResult == true) {
				return Message.createServerResponseMessage(isCorrectMessage);
			} else {
				return new String("{\"Failure during encryption\"}");
			}
		default:
			return new String("{\"Failure\"}");
		}
	}

	/**
	 * Processes incoming orders. Values of messages are read out and validation
	 * process gets started. Server sends back a response to client showing if
	 * incoming order signature could be validated
	 * 
	 * @param message incoming from interaction of client with server
	 * @return
	 */
	public String acceptMessage(String message) {

		boolean isCorrectMessage = false;
		MessageType type = null;
		int clientId = 0;
		ObjectMapper mapper = new ObjectMapper();
		try {
			SignedMessage signedMessage = mapper.readValue(message, SignedMessage.class);
			clientId = signedMessage.getClientId();

			byte[] signature = signedMessage.getSignature();

			isCorrectMessage = checkSignature(clientId, signedMessage.getContent().getBytes(), signature);

			if (isCorrectMessage == true) {
				Message theMessage = mapper.readValue(signedMessage.getContent(), Message.class);
				type = theMessage.getMessageType();

				p(theMessage.getMessageType().toString());

				return parseMessage(type, clientId, isCorrectMessage, signedMessage);

			} else {
				return Message.createServerResponseMessage(isCorrectMessage);
			}
		} catch (JsonProcessingException e) {
			p("Exception " + e.getLocalizedMessage());
			return new String("{\"Failure\"}");
		}
	}

	/**
	 * Auxiliary method for showing some responses/requests in the communication
	 * between client and server
	 * 
	 * @param s
	 */
	private void p(String s) {
		System.out.println(Instant.now().toString() + " server: " + s);
	}

	@Override
	public void run() {
		while (true) {
			p("processing orders");
			try {
				Thread.sleep((long) (Math.random() * sendFrequency + 1));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
