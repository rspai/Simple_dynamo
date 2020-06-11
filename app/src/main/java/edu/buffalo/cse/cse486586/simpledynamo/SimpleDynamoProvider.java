package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

public class SimpleDynamoProvider extends ContentProvider {
	static final String TAG = SimpleDynamoProvider.class.getSimpleName();
	static final int SERVER_PORT = 10000;
	static final String[] REMOTE_PORT = {"11108", "11112", "11116", "11120", "11124"};
	Uri providerUri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider");
	int predeccessorPort = 0;
	int currentPort = 0;
	int successorPort = 0;
	String hashOfCurrentNode = "";
	int prevNode1 = 0;
	int prevNode2 = 0;
	int nextNode1 = 0;
	int nextNode2 = 0;
	int isRejoining = 0;
	HashMap<Integer, String> avdHashMap = new HashMap<Integer, String>();

	public int getSelectionType(String s) {
		int i = 0;
		if(s.compareTo("@") == 0) {
			i = 1;
		} else if(s.compareTo("*") == 0) {
			i = 2;
		}
		return i;
	}

	public int checkPredecessorSuccessor(int predecessorPort, int successorPort) {
		int op = 0;
		if(predecessorPort == 0 && successorPort == 0) {
			Log.i(TAG, "Has no predecessor port and no successor port");
			op = 1;
		} else if(predecessorPort == 0 && successorPort != 0) {
			Log.i(TAG, "Has successor port but no predecessor port");
			op = 2;
		} else if(predecessorPort != 0 && successorPort == 0) {
			Log.i(TAG, "Has predecessor port but no successor port");
			op = 3;
		} else if(predecessorPort != 0 && successorPort != 0) {
			Log.i(TAG, "Has predecessor port as well as successor port");
			op = 4;
		}
		return op;
	}

	public int getSelectionLen(String selection) {
		int l = 0;
		if(selection.length() == 1) {
			Log.i(TAG, "1 AVD alive");
			l = 1;
		} else if(selection.length() == 5) {
			Log.i(TAG, "1-5 AVDs alive");
			l = 2;
		} else if(selection.length() > 5 || selection.length() > 6) {
			Log.i(TAG, "all AVDs alive");
			l = 3;
		} else {
			Log.i(TAG, "unknown value");
			l = 4;
		}
		return l;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub

		/* reference taken from: https://stackoverflow.com/questions/3554722/how-to-delete-internal-storage-file-in-android */
		//code added starts
		String[] cpFiles = new String[] {};
		int i = 0;
		String deleteStr = null;
		String deleteStr1 = null;
		String response = null;

		try {
			cpFiles = getContext().getFilesDir().list();
		} catch (NullPointerException e) {
			Log.i(TAG, "NullPointerException: delete(): No file fetched");
			e.printStackTrace();
		} catch (Exception e) {
			Log.i(TAG, "Error fetching files");
			e.printStackTrace();
		}
		deleteStr = "Delete:" + successorPort + ":" + selection;
		int sel = getSelectionType(selection);
		int checkSP = checkPredecessorSuccessor(predeccessorPort, successorPort);
		int len = getSelectionLen(selection);

		try {
			if(checkSP == 1) {
				for(i = 0; i < cpFiles.length; i++) {
					if(getContext().deleteFile(cpFiles[i])) {
						Log.i(TAG, "delete file successful");
					} else {
						Log.i(TAG, "delete file failed");
					}
				}
			} else if(len == 1) {
				if(sel == 1) {
					for(i = 0; i < cpFiles.length; i++) {
						if(getContext().deleteFile(cpFiles[i])) {
							Log.i(TAG, "delete file successful");
						} else {
							Log.i(TAG, "delete file failed");
						}
					}
				} else if(sel == 2) {
					Log.i(TAG, "DeleteStr: " + deleteStr);
					try {
						response = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteStr).get();
						Log.i(TAG, "At global delete, response received: " + response);
						Log.i(TAG, "response received, now delete all files");
						for(i = 0; i < cpFiles.length; i++) {
							if(getContext().deleteFile(cpFiles[i])) {
								Log.i(TAG, "delete file successful");
							} else {
								Log.i(TAG, "delete file failed");
							}
						}
					} catch (InterruptedException e) {
						Log.e(TAG, "InterruptedException in delete");
						e.printStackTrace();
					} catch (ExecutionException e) {
						Log.e(TAG, "ExecutionException in delete");
						e.printStackTrace();
					} catch (Exception e) {
						Log.e(TAG, "Exception in delete");
						e.printStackTrace();
					}
				}
			} else if(len == 2) {
				if(selection.substring(0, 1).compareTo("*") == 0) {
					Log.i(TAG, "delete all files on all AVDs");
					String sp = String.valueOf(successorPort);
					if(selection.contains(sp)) {
						Log.i(TAG, "successor port in list of ports");
					} else {
						Log.i(TAG, "not in list, delete files");
						for(i = 0; i < cpFiles.length; i++) {
							if(getContext().deleteFile(cpFiles[i])) {
								Log.i(TAG, "delete file successful");
							} else {
								Log.i(TAG, "delete file failed");
							}
						}
					}
					Log.i(TAG, "delete remaining");
					for(i = 0; i < cpFiles.length; i++) {
						if(getContext().deleteFile(cpFiles[i])) {
							Log.i(TAG, "delete file successful");
						} else {
							Log.i(TAG, "delete file failed");
						}
					}
				} else {
					Log.i(TAG, "nothing to delete");
				}
			} else if(len == 3) {
				for(i = 0; i < cpFiles.length; i++) {
					if(selection.contains(cpFiles[i])) {
						if(getContext().deleteFile(cpFiles[i])) {
							Log.i(TAG, "delete file successful");
						} else {
							Log.i(TAG, "delete file failed");
						}
					}
				}
				int[] portStr = {nextNode1, nextNode2};
				for(i = 0; i < 2; i++) {
					deleteStr1 = "Delete_Duplicate:";
					deleteStr1 = deleteStr1 + portStr[i] + ":" + selection;
					Log.i(TAG, "DeleteStr: " + deleteStr1);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteStr1);
				}
			}
			else {
				Log.i(TAG, "DeleteStr: " + deleteStr);
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteStr);
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: delete()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: delete()");
			e.printStackTrace();
		}
		//code added ends
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	public void updateHashValues() {
		String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
		int thisPort = 0;
		String hashOfPort = "";
		try {
			for(int i = 0; i < REMOTE_PORTS.length; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				if (avdHashMap.isEmpty() || !avdHashMap.containsKey(thisPort)) {
					hashOfPort = genHash(String.valueOf(thisPort));
					avdHashMap.put(thisPort, hashOfPort);
				} else {
					Log.i(TAG, "value exists as: " + avdHashMap.get(thisPort));
				}
			}
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "NoSuchAlgorithmException");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: updateHashValues");
			e.printStackTrace();
		}
	}

	public String getNode(String inStr) {
		String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
		String node = String.valueOf(Integer.parseInt(REMOTE_PORTS[0]) / 2);
		int thisPort = 0;
		int nextPort = 0;
		String hashOfPort = "";
		String hashOfNextPort = "";

		try {
			updateHashValues();
			for (int i = 0; i < REMOTE_PORTS.length - 1; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				nextPort = Integer.parseInt(REMOTE_PORTS[(i + 1) % 5]) / 2;
				updateHashValues();
				if(avdHashMap.isEmpty() || !avdHashMap.containsKey(thisPort)) {
					Log.i(TAG, "entry not found");
				} else {
					hashOfPort = avdHashMap.get(thisPort);
				}
				Log.i(TAG, "Port: " + thisPort + ", genHash on this port: " + hashOfPort);
				updateHashValues();
				if(avdHashMap.isEmpty() || !avdHashMap.containsKey(nextPort)) {
					Log.i(TAG, "entry not found");
				} else {
					hashOfNextPort = avdHashMap.get(nextPort);
				}
				Log.i(TAG, "hash of next port: " + hashOfNextPort + ", for port: " + nextPort);
				if (i != (REMOTE_PORTS.length - 1) && hashOfPort.compareTo(inStr) < 0 && inStr.compareTo(hashOfNextPort) <= 0) {
					node = String.valueOf(nextPort);
				} else {
					Log.i(TAG, "node value not found");
				}
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: getNode()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: getNode()");
			e.printStackTrace();
		}
		return node;
	}

	public String getNodeValue(String inStr) {
		int thisPort = 0;
		int nextPort = 0;
		String hashOfPort = "";
		String hashOfNextPort = "";
		String hashOfStr = "";
		String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
		String node = String.valueOf(Integer.parseInt(REMOTE_PORTS[0]) / 2);;
		try {
			for (int i = 0; i < REMOTE_PORTS.length; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				nextPort = Integer.parseInt(REMOTE_PORTS[(i + 1) % 5]) / 2;
				updateHashValues();
				if(avdHashMap.isEmpty() || !avdHashMap.containsKey(thisPort)) {
					Log.i(TAG, "entry not found");
				} else {
					hashOfPort = avdHashMap.get(thisPort);
				}
				Log.i(TAG, "Port: " + thisPort + ", genHash on this port: " + hashOfPort);
				updateHashValues();
				if(avdHashMap.isEmpty() || !avdHashMap.containsKey(nextPort)) {
					Log.i(TAG, "entry not found");
				} else {
					hashOfNextPort = avdHashMap.get(nextPort);
				}
				Log.i(TAG, "hash of next port: " + hashOfNextPort + ", for port: " + nextPort);
				try {
					hashOfStr = genHash(inStr);
				} catch (NoSuchAlgorithmException e) {
					Log.e(TAG, "NoSuchAlgorithmException");
					e.printStackTrace();
				}
				Log.i(TAG, "hash of input here: " + hashOfStr);
				if (i != (REMOTE_PORTS.length - 1) && hashOfPort.compareTo(hashOfStr) < 0 && hashOfStr.compareTo(hashOfNextPort) <= 0) {
					node = String.valueOf(nextPort);
				}
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: getNodeValue()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: getNodeValue()");
			e.printStackTrace();
		}
		return node;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub

		//using some code from pa3
		//code added starts
		String insertStr = null;
		String insertStr1 = null;
		String hashOfKey = "";
		int thisPort = 0;
		int nextPort1 = 0;
		int nextPort2 = 0;
		String node = "";
		FileOutputStream fileOutputStream = null;

		try {
			String key = values.getAsString("key");
			String value = values.getAsString("value");
			key = key.trim();
			value = value.trim();
			Log.i(TAG, "key: " + key);
			Log.i(TAG, "value: " + value);
			Log.i(TAG, "Currently at Port: " + currentPort);
			Log.i(TAG, "Hash of present node: " + hashOfCurrentNode);
			String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
			try {
				hashOfKey = genHash(key);
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, "error getting hash value");
				e.printStackTrace();
			}
			node = getNode(hashOfKey);
			Log.i(TAG, "Node is mapped to: " + node);
			if(currentPort != Integer.parseInt(node)) {
				insertStr = "Insert:" + node + ":" + key + ":" + value;
				Log.i(TAG, "insertStr: " + insertStr);
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertStr);
			} else {
				Log.i(TAG, "write all values to file");
				fileOutputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
				OutputStreamWriter outputStreamWriter = new	OutputStreamWriter(fileOutputStream);
				outputStreamWriter.write(value);
				outputStreamWriter.flush();
				outputStreamWriter.close();
				fileOutputStream.close();
			}
			for(int i = 0; i < REMOTE_PORTS.length; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				if(thisPort == Integer.parseInt(node)) {
					nextPort1 = Integer.parseInt(REMOTE_PORTS[(i + 1) % 5]) / 2;
					nextPort2 = Integer.parseInt(REMOTE_PORTS[(i + 2) % 5]) / 2;
				} else {
					Log.i(TAG, "node not found");
				}
			}
			Log.i(TAG, "successor nodes are : " + nextPort1 + " and " + nextPort2);
			int[] nextNodes = {nextPort1, nextPort2};
			for(int i = 0; i < 2; i++) {
				insertStr1= "Insert_Duplicate:";
				insertStr1 = insertStr1 + nextNodes[i] + ":" + key + ":" + value;
				Log.i(TAG, "insertStr1: " + insertStr1);
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertStr1);
			}
			try {
				getContext().getContentResolver().notifyChange(providerUri, null);
			} catch (NullPointerException e) {
				Log.e(TAG, "NullPointerException: calling getContentResolver()");
				e.printStackTrace();
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: insert()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: insert()");
			e.printStackTrace();
		}
		//code added ends
		return null;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub

		//using some code from pa3
		//code added starts
		TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		final String myPort = String.valueOf(Integer.parseInt(portStr) * 2);

		ServerSocket serverSocket = null;
		int thisPort = 0;
		int prevPort1 = 0;
		int prevPort2 = 0;
		int nextPort1 = 0;
		int nextPort2 = 0;
		String onCreateStr = "";

		try {
			serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {
			Log.e(TAG, "Can't create a ServerSocket");
			e.printStackTrace();
			return false;
		}
		try {
			currentPort = Integer.parseInt(myPort) / 2;
			Log.i(TAG, "Currently at port: " + currentPort);
			try {
				hashOfCurrentNode = genHash(String.valueOf(currentPort));
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG, "NoSuchAlgorithmException");
				e.printStackTrace();
			}
			Log.i(TAG, "hash of this node: " + hashOfCurrentNode);
			String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
			for (int i = 0; i < REMOTE_PORTS.length; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				if (thisPort == currentPort) {
					nextPort1 = Integer.parseInt(REMOTE_PORTS[(i + 1) % 5]) / 2;
					nextPort2 = Integer.parseInt(REMOTE_PORTS[(i + 2) % 5]) / 2;
				} else {
					Log.i(TAG, "node not found");
				}
			}
			nextNode1 = nextPort1;
			nextNode2 = nextPort2;
			Log.i(TAG, "successor ports are: " + nextNode1 + " and " + nextNode2);
			successorPort = nextPort1;
			for (int i = 0; i < REMOTE_PORTS.length; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				if (thisPort == currentPort) {
					if (i - 1 < 0) {
						prevPort1 = Integer.parseInt(REMOTE_PORTS[(((i - 1) % 5) + 5) % 5]) / 2;
					} else {
						prevPort1 = Integer.parseInt(REMOTE_PORTS[(i - 1) % 5]) / 2;
					}
					if (i - 2 < 0) {
						prevPort2 = Integer.parseInt(REMOTE_PORTS[(((i - 2) % 5) + 5) % 5]) / 2;
					} else {
						prevPort2 = Integer.parseInt(REMOTE_PORTS[(i - 2) % 5]) / 2;
					}
				} else {
					Log.i(TAG, "node not found");
				}
			}
			prevNode1 = prevPort1;
			prevNode2 = prevPort2;
			Log.i(TAG, "predeccessor ports are: " + prevNode1 + " and " + prevNode2);
			predeccessorPort = prevPort1;
			String[] cpFiles = new String[]{};
			try {
				cpFiles = getContext().getFilesDir().list();
			} catch (NullPointerException e) {
				Log.i(TAG, "NullPointerException: delete(): No file fetched");
				e.printStackTrace();
			} catch (Exception e) {
				Log.i(TAG, "Error fetching files");
				e.printStackTrace();
			}
			for(int i = 0; i < cpFiles.length; i++) {
				if(getContext().deleteFile(cpFiles[i])) {
					Log.i(TAG, "delete file successful");
				} else {
					Log.i(TAG, "delete file failed");
				}
			}
			for(int i = 0; i < REMOTE_PORTS.length; i++) {
				thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
				if(thisPort == currentPort) {
					Log.i(TAG, "this port is alive");
				} else {
					onCreateStr = "Join:" + thisPort + ":" + currentPort;
					String response = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, onCreateStr).get();
					Log.i(TAG, "response received as: " + response);
				}
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: onCreate()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: onCreate()");
			e.printStackTrace();
		}
		//code added ends
		return false;
	}

	public void getCursor(MatrixCursor cursor, String[] cpFiles, String selection, int cnt) {
		try {
			BufferedReader bufferedReader = null;
			int i = 0;
			if (cnt == 1) {
				for (i = 0; i < cpFiles.length; i++) {
					try {
						bufferedReader = new BufferedReader(new InputStreamReader(getContext().openFileInput(cpFiles[i])));
						String line = bufferedReader.readLine();
						String[] matrixRow = {cpFiles[i], line};
						cursor.addRow(matrixRow);
						bufferedReader.close();
					} catch (IOException e) {
						Log.e(TAG, "IOException");
						e.printStackTrace();
					} catch (Exception e) {
						Log.e(TAG, "Exception");
						e.printStackTrace();
					}
				}
			} else if (cnt == 2) {
				try {
					bufferedReader = new BufferedReader(new InputStreamReader(getContext().openFileInput(selection)));
					String line = bufferedReader.readLine();
					String[] matrixRow = {selection, line};
					cursor.addRow(matrixRow);
					bufferedReader.close();
				} catch (IOException e) {
					Log.e(TAG, "IOException");
					e.printStackTrace();
				} catch (Exception e) {
					Log.e(TAG, "Exception");
					e.printStackTrace();
				}
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: getCursor()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: getCursor()");
			e.printStackTrace();
		}
	}

	public void processQueryFromClient(MatrixCursor cursor, String msg) {
		try {
			String[] clientMsg = msg.split(",");
			Log.i(TAG, "Message elements fetched:");
			for (int i = 0; i < clientMsg.length; i++) {
				Log.i(TAG, clientMsg[i]);
			}
			for(int i = 0; i < clientMsg.length; i++) {
				clientMsg[i] = clientMsg[i].trim();
				clientMsg[i] = clientMsg[i].replaceAll("\\{", "");
				clientMsg[i] = clientMsg[i].trim();
				String[] pairs = clientMsg[i].split("=");
				pairs[0] = pairs[0].trim();
				pairs[1] = pairs[1].trim();
				int k = pairs[1].length() - 1;
				if((pairs[1].substring(k)).compareTo("}") == 0) {
					pairs[1] = pairs[1].substring(0, k);
					pairs[1] = pairs[1].trim();
				} else {
					pairs[1] = pairs[1].trim();
				}
				String[] matrixRow = {pairs[0], pairs[1]};
				cursor.addRow(matrixRow);
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: processQueryFromClient()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Error: processQueryFromClient");
			e.printStackTrace();
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub

		//using some code from pa3
		//code added starts
		int thisPort = 0;
		int nextPort1 = 0;
		int nextPort2 = 0;
		String queryStr = "";
		String queryFromClient = "";

		String[] cpFiles = new String[]{};
		try {
			cpFiles = getContext().getFilesDir().list();
		} catch (NullPointerException e) {
			Log.i(TAG, "NullPointerException: query(): No file fetched");
			e.printStackTrace();
		} catch (Exception e) {
			Log.i(TAG, "Error fetching files");
			e.printStackTrace();
		}
		MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
		cursor.moveToFirst();
		selection = selection.trim();
		int sel = getSelectionType(selection);
		int checkSP = checkPredecessorSuccessor(predeccessorPort, successorPort);
		int len = getSelectionLen(selection);
		BufferedReader bufferedReader = null;

		try {
			if (checkSP == 1) {
				if (len == 1) {
					for (int i = 0; i < cpFiles.length; i++) {
						try {
							bufferedReader = new BufferedReader(new InputStreamReader(getContext().openFileInput(cpFiles[i])));
							String line = bufferedReader.readLine();
							String[] matrixRow = {cpFiles[i], line};
							cursor.addRow(matrixRow);
							bufferedReader.close();
						} catch (IOException e) {
							Log.e(TAG, "IOException");
							e.printStackTrace();
						}
					}
					return cursor;
				} else if (len > 1) {
					getCursor(cursor, cpFiles, selection, 2);
					return cursor;
				}
			} else if (sel == 1) {
				if (len == 1) {
					for (int i = 0; i < cpFiles.length; i++) {
						try {
							bufferedReader = new BufferedReader(new InputStreamReader(getContext().openFileInput(cpFiles[i])));
							String line = bufferedReader.readLine();
							String[] matrixRow = {cpFiles[i], line};
							cursor.addRow(matrixRow);
							bufferedReader.close();
						} catch (IOException e) {
							Log.e(TAG, "IOException");
							e.printStackTrace();
						}
					}
					return cursor;
				} else if (len > 1) {
					getCursor(cursor, cpFiles, selection, 2);
					return cursor;
				}
			} else if (sel == 2) {
				for (int i = 0; i < cpFiles.length; i++) {
					try {
						bufferedReader = new BufferedReader(new InputStreamReader(getContext().openFileInput(cpFiles[i])));
						String line = bufferedReader.readLine();
						String[] matrixRow = {cpFiles[i], line};
						cursor.addRow(matrixRow);
						bufferedReader.close();
					} catch (IOException e) {
						Log.e(TAG, "IOException");
						e.printStackTrace();
					}
				}
				String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
				for (int j = 0; j < REMOTE_PORTS.length; j++) {
					thisPort = Integer.parseInt(REMOTE_PORTS[j]) / 2;
					if (thisPort == currentPort) {
						Log.i(TAG, "this port is alive");
					} else {
						queryStr = "Query:" + thisPort + ":@";
						try {
							queryFromClient = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryStr).get();
						} catch (Exception e) {
							Log.e(TAG, "exception");
							e.printStackTrace();
							continue;
						}
						processQueryFromClient(cursor, queryFromClient);
					}
				}
				return cursor;
			} else if (len == 3) {
				int y = 0;
				for (int i = 0; i < cpFiles.length; i++) {
					if (cpFiles[i].contains(selection)) {
						y = 1;
					} else {
						Log.i(TAG, "file name not found in string");
					}
				}
				if (y == 1) {
					getCursor(cursor, cpFiles, selection, 2);
					return cursor;
				} else {
					Socket s = null;
					DataOutputStream dataOutputStream = null;
					DataInputStream dataInputStream = null;
					try {
						String node = getNodeValue(selection);
						Log.i(TAG, "Node is mapped to: " + node);
						String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
						for (int i = 0; i < REMOTE_PORTS.length; i++) {
							thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
							if (thisPort == Integer.parseInt(node)) {
								nextPort1 = Integer.parseInt(REMOTE_PORTS[(i + 1) % 5]) / 2;
								nextPort2 = Integer.parseInt(REMOTE_PORTS[(i + 2) % 5]) / 2;
							} else {
								Log.i(TAG, "node not found");
							}
						}
						int[] portToSend = {nextPort1, nextPort2, Integer.parseInt(node)};
						String queryStrIn = "";
						for (int i = 0; i < portToSend.length; i++) {
							if (portToSend[i] == currentPort) {
								Log.i(TAG, "currently at port: " + currentPort);
								continue;
							}
							Log.i(TAG, "sending data to port: " + portToSend[i]);
							int destPort = portToSend[i] * 2;
							try {
								s = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), destPort);
							} catch (Exception e) {
								Log.e(TAG, "Error creating socket");
								e.printStackTrace();
							}
							try {
								dataOutputStream = new DataOutputStream(s.getOutputStream());
								dataOutputStream.writeUTF("Query:" + portToSend[i] + ":" + selection);
								dataOutputStream.flush();
							} catch (IOException e) {
								Log.e(TAG, "IOException");
								e.printStackTrace();
							}
							try {
								dataInputStream = new DataInputStream(s.getInputStream());
								queryStrIn = dataInputStream.readUTF();
							} catch (IOException e) {
								Log.e(TAG, "IOException");
								e.printStackTrace();
								continue;
							}
							Log.i(TAG, "queryStrIn is: " + queryStrIn);
							int l = 66 / 2 + 1;
							queryStrIn = queryStrIn.substring(l);
							int k = queryStrIn.length() - 1;
							if (queryStrIn.substring(k).compareTo("}") == 0) {
								queryStrIn = queryStrIn.substring(0, k);
								queryStrIn = queryStrIn.trim();
							} else {
								queryStrIn = queryStrIn.trim();
							}
							String[] matrixRow = {selection, queryStrIn};
							cursor.addRow(matrixRow);
							s.close();
							dataInputStream.close();
							dataOutputStream.close();
							break;
						}
					} catch (Exception e) {
						Log.e(TAG, "Exception e");
						e.printStackTrace();
					}
					return cursor;
				}
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: query()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: query()");
			e.printStackTrace();
		}
		//code added ends
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	public int getOperationType(String str) {
		int op = 0;
		try {
			if(str.compareTo("Delete") == 0) {
				Log.i(TAG, "This is delete function call");
				op = 1;
			} else if(str.compareTo("Delete_Duplicate") == 0) {
				Log.i(TAG, "This is delete function call");
				op = 2;
			} else if(str.compareTo("Insert") == 0) {
				Log.i(TAG, "This is insert function call");
				op = 3;
			} else if(str.compareTo("Insert_Duplicate") == 0) {
				Log.i(TAG, "This is insert function call");
				op = 4;
			} else if(str.compareTo("Join") == 0) {
				Log.i(TAG, "This is onCreate function call");
				op = 5;
			} else if(str.compareTo("Rejoin") == 0) {
				Log.i(TAG, "This is onCreate function call for rejoining node");
				op = 6;
			} else if(str.compareTo("Query") == 0) {
				Log.i(TAG, "This is query function call");
				op = 7;
			} else {
				Log.i(TAG, "operation type invalid");
				op = 8;
			}
		} catch (NoSuchFieldError e) {
			Log.e(TAG, "NoSuchFieldError: getOperationType()");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, "Exception: getOperationType()");
			e.printStackTrace();
		}
		return op;
	}

	//using some code from pa3
	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected synchronized Void doInBackground(ServerSocket... sockets) {
			DataInputStream dataIn = null;
			DataOutputStream dataOut = null;
			String inputStr = "";
			HashMap<String, String> keyValueMap = new HashMap<String, String>();
			ServerSocket serverSocket = sockets[0];

			try {
				while (true) {
					Socket socket = serverSocket.accept();
					try {
						dataIn = new DataInputStream(socket.getInputStream());
						inputStr = dataIn.readUTF();
					} catch (IOException e) {
						Log.e(TAG, "DataInputStream: IOException");
						e.printStackTrace();
					}
					Log.i(TAG, "input string read: " + inputStr);
					try {
						dataOut = new DataOutputStream(socket.getOutputStream());
					} catch (IOException e) {
						Log.e(TAG, "DataOutputStream: IOException");
						e.printStackTrace();
					}
					String[] inMsg = inputStr.split(":");
					String optype = inMsg[0];
					int oT = getOperationType(optype);

					if(oT == 1) {
						String s1;
						String typeOps = inMsg[2];
						if(typeOps.compareTo("*") == 0) {
							s1 = typeOps + inMsg[1];
						} else {
							s1 = inputStr;
						}
						Log.i(TAG, "calling delete!");
						delete(providerUri, s1, null);
					}
					else if(oT == 2) {
						String[] cpFiles = new String[]{};
						try {
							cpFiles = getContext().getFilesDir().list();
						} catch (NullPointerException e) {
							Log.i(TAG, "NullPointerException: query(): No file fetched");
							e.printStackTrace();
						} catch (Exception e) {
							Log.i(TAG, "Error fetching files");
							e.printStackTrace();
						}
						for(int i = 0; i < cpFiles.length; i++) {
							if(inMsg[2].compareTo(cpFiles[i]) == 0) {
								if(getContext().deleteFile(cpFiles[i])) {
									Log.i(TAG, "delete file successful");
								} else {
									Log.i(TAG, "delete file failed");
								}
							} else {
								Log.i(TAG, "not found");
							}
						}
					}
					else if(oT == 3) {
						String key = inMsg[2];
						String value = inMsg[3];
						key = key.trim();
						value = value.trim();
						ContentValues keyValueToInsert = new ContentValues();
						Log.i(TAG, "key: " + key);
						Log.i(TAG, "value: " + value);
						keyValueToInsert.put("key", key);
						keyValueToInsert.put("value", value);
						if(keyValueMap.isEmpty() || !keyValueMap.containsKey(key)) {
							Log.i(TAG, "entry not found, so insert");
							keyValueMap.put(key, value);
						} else {
							Log.i(TAG, "remove old entry, add new");
							keyValueMap.remove(key);
							keyValueMap.put(key, value);
						}
						Log.i(TAG, "calling insert!");
						insert(providerUri, keyValueToInsert);
					}
					else if(oT == 4) {
						String key = inMsg[2];
						String value = inMsg[3];
						key = key.trim();
						value = value.trim();
						Log.i(TAG, "key: " + key);
						Log.i(TAG, "value: " + value);
						FileOutputStream fileOutputStream = null;
						fileOutputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
						OutputStreamWriter outputStreamWriter = new	OutputStreamWriter(fileOutputStream);
						outputStreamWriter.write(value);
						outputStreamWriter.flush();
						outputStreamWriter.close();
						fileOutputStream.close();
					}
					else if(oT == 5) {
						isRejoining = 1;
						MatrixCursor matrixCursor = new MatrixCursor(new String[]{"key", "value"});
						String[] cpFiles = new String[]{};
						try {
							cpFiles = getContext().getFilesDir().list();
						} catch (NullPointerException e) {
							Log.i(TAG, "NullPointerException: query(): No file fetched");
							e.printStackTrace();
						} catch (Exception e) {
							Log.i(TAG, "Error fetching files");
							e.printStackTrace();
						}
						BufferedReader bufferedReader = null;
						for(int i = 0; i < cpFiles.length; i++) {
							try {
								bufferedReader = new BufferedReader(new InputStreamReader(getContext().openFileInput(cpFiles[i])));
								String line = bufferedReader.readLine();
								line = line.trim();
								cpFiles[i] = cpFiles[i].trim();
								String[] matrixRow = {cpFiles[i], line};
								matrixCursor.addRow(matrixRow);
								bufferedReader.close();
							} catch (Exception e) {
								Log.i(TAG, "exception");
								e.printStackTrace();
							}
						}
						int c = matrixCursor.getCount();
						if(c > 0) {
							String[] REMOTE_PORTS = {REMOTE_PORT[4], REMOTE_PORT[1], REMOTE_PORT[0], REMOTE_PORT[2], REMOTE_PORT[3]};
							int thisPort = 0;
							int prevPort1 = 0;
							int prevPort2 = 0;
							for (int i = 0; i < REMOTE_PORTS.length; i++) {
								thisPort = Integer.parseInt(REMOTE_PORTS[i]) / 2;
								if(thisPort == Integer.parseInt(inMsg[2])) {
									if(i - 1 < 0) {
										prevPort1 = Integer.parseInt(REMOTE_PORTS[(((i - 1) % 5) + 5) % 5]) / 2;
									} else {
										prevPort1 = Integer.parseInt(REMOTE_PORTS[(i - 1) % 5]) / 2;
									}
									if(i - 2 < 0) {
										prevPort2 = Integer.parseInt(REMOTE_PORTS[(((i - 2) % 5) + 5) % 5]) / 2;
									} else {
										prevPort2 = Integer.parseInt(REMOTE_PORTS[(i - 2) % 5]) / 2;
									}
								} else {
									Log.i(TAG, "node not found");
								}
							}
							matrixCursor.moveToFirst();
							ArrayList<String> listOfNodes = new ArrayList<String>();
							HashMap<String, String> hashMapNodes = new HashMap<String, String>();
							String keyToAdd = "";
							String valueToAdd = "";
							/* reference taken from: https://www.codota.com/code/java/classes/android.database.Cursor ,
                            	https://www.codota.com/code/java/methods/android.database.Cursor/moveToNext ,
                            	https://examples.javacodegeeks.com/android/core/database/android-cursor-example/ ,
                            	https://stackoverflow.com/questions/10723770/whats-the-best-way-to-iterate-an-android-cursor
                        	*/
							try {
								if(matrixCursor != null) {
									for(matrixCursor.moveToFirst(); !matrixCursor.isAfterLast(); matrixCursor.moveToNext()) {
										keyToAdd = matrixCursor.getString(matrixCursor.getColumnIndex("key"));
										valueToAdd = matrixCursor.getString(matrixCursor.getColumnIndex("value"));
										keyToAdd = keyToAdd.trim();
										valueToAdd = valueToAdd.trim();
										String node = getNodeValue(keyToAdd);
										int[] portList = {Integer.parseInt(inMsg[2]), prevPort1, prevPort2};
										int y = 0;
										for(int i = 0; i < portList.length; i++) {
											if(portList[i] == Integer.parseInt(node)) {
												y = 1;
											} else {
												Log.i(TAG, "node not found in list");
											}
										}
										if(y == 1) {
											if(listOfNodes.add(keyToAdd)) {
												Log.i(TAG, "element added successfully");
											} else {
												Log.i(TAG, "error adding");
											}
											if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(keyToAdd)) {
												hashMapNodes.put(keyToAdd, valueToAdd);
											} else {
												hashMapNodes.remove(keyToAdd);
												hashMapNodes.put(keyToAdd, valueToAdd);
											}
										} else {
											Log.i(TAG, "not found");
										}
									}
									matrixCursor.close();
								} else {
									Log.i(TAG, "cursor fetched no rows");
								}
							} catch (Exception e) {
								Log.e(TAG, "Exception at cursor operation");
								e.printStackTrace();
							}
							String strToSend = "{";
							Iterator<String> iter = listOfNodes.iterator();
							while(iter.hasNext()) {
								String key = iter.next();
								String val = null;
								if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(key)) {
									Log.i(TAG, "no entry found");
								} else {
									val = hashMapNodes.get(key);
									Log.i(TAG, "entry found as: " + val);
								}
								strToSend = strToSend + key + "=" + val + ",";
							}
							if(strToSend.substring(strToSend.length()-1).compareTo(",") == 0) {
								strToSend = strToSend.substring(0, strToSend.length() - 1) + "}";
							} else {
								strToSend = strToSend + "}";
							}
							Log.i(TAG, "string to be sent: " + strToSend);
							String joinStr = "Rejoin:" + inMsg[2] + ":" + strToSend;
							new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, joinStr);
						}
						isRejoining = 0;
					}
					else if(oT == 6) {
						if (inMsg[2].length() > 2) {
							inMsg[2] = inMsg[2].replaceAll("\\{", "");
							inMsg[2] = inMsg[2].trim();
							inMsg[2] = inMsg[2].replaceAll("\\}", "");
							inMsg[2] = inMsg[2].trim();
							String newMsg = "empty";
							for(int i = 0; i < inMsg[2].length(); i++) {
								if(i != inMsg[2].length() - 1) {
									if((!inMsg[2].substring(i, i + 1).equals("{")) || (!inMsg[2].substring(i, i + 1).equals("}"))) {
										if(newMsg.compareTo("empty") == 0) {
											newMsg = inMsg[2].substring(i, i + 1);
										} else {
											newMsg = newMsg + inMsg[2].substring(i, i + 1);
										}
									}
								} else if(i == inMsg[2].length() - 1) {
									if((!inMsg[2].substring(i).equals("{")) || (!inMsg[2].substring(i).equals("}"))) {
										newMsg = newMsg + inMsg[2].substring(i);
									}
								}
							}
							String[] inValue = inMsg[2].split(",");
							ArrayList<String> listOfNodes = new ArrayList<String>();
							HashMap<String, String> hashMapNodes = new HashMap<String, String>();
							String keyToAdd = "";
							String valueToAdd = "";
							for(int i = 0; i < inValue.length; i++) {
								try {
									String[] pairs = inValue[i].split("=");
									keyToAdd = pairs[0];
									valueToAdd = pairs[1];
									keyToAdd = keyToAdd.trim();
									valueToAdd = valueToAdd.trim();
									Log.i(TAG,"keyToAdd: " + keyToAdd);
									Log.i(TAG, "valueToAdd" + valueToAdd);
									if(listOfNodes.add(keyToAdd)) {
										Log.i(TAG, "element added successfully");
									} else {
										Log.i(TAG, "error adding");
									}
									if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(keyToAdd)) {
										hashMapNodes.put(keyToAdd, valueToAdd);
									} else {
										hashMapNodes.remove(keyToAdd);
										hashMapNodes.put(keyToAdd, valueToAdd);
									}
								} catch (Exception e) {
									Log.e(TAG, "Exception");
									e.printStackTrace();
								}
							}
							String[] cpFiles = new String[]{};
							try {
								cpFiles = getContext().getFilesDir().list();
							} catch (NullPointerException e) {
								Log.i(TAG, "NullPointerException: query(): No file fetched");
								e.printStackTrace();
							} catch (Exception e) {
								Log.i(TAG, "Error fetching files");
								e.printStackTrace();
							}
							Iterator<String> iter = listOfNodes.iterator();
							while(iter.hasNext()) {
								String key = iter.next();
								String val = "";
								if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(key)) {
									Log.i(TAG, "no entry found");
								} else {
									val = hashMapNodes.get(key);
									Log.i(TAG, "entry found as: " + val);
								}
								int y = 0;
								for(int i = 0; i < cpFiles.length; i++) {
									if(cpFiles[i].contains(key)) {
										y = 1;
									} else {
										Log.i(TAG, "file name not found in string");
									}
								}
								if(y == 0) {
									FileOutputStream fileOutputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
									OutputStreamWriter outputStreamWriter = new	OutputStreamWriter(fileOutputStream);
									outputStreamWriter.write(val);
									outputStreamWriter.flush();
									outputStreamWriter.close();
									fileOutputStream.close();
								} else {
									Log.i(TAG, "value not found");
								}
							}
						}
						getContext().getContentResolver().notifyChange(providerUri, null);
					}
					else if(oT == 7) {
						ArrayList<String> listOfNodes = new ArrayList<String>();
						HashMap<String, String> hashMapNodes = new HashMap<String, String>();
						String typeOps = inMsg[2];
						String selectionString = "";
						if(typeOps.compareTo("*") == 0) {
							selectionString = "*";
						} else {
							selectionString = typeOps;
						}
						Cursor cursor = query(providerUri, null, selectionString, null, null);
						try {
							if(cursor != null) {
								String keyToAdd = "";
								String valueToAdd = "";
								for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
									keyToAdd = cursor.getString(cursor.getColumnIndex("key"));
									valueToAdd = cursor.getString(cursor.getColumnIndex("value"));
									if(listOfNodes.add(keyToAdd)) {
										Log.i(TAG, "element added successfully");
									} else {
										Log.i(TAG, "error adding");
									}
									if(typeOps.compareTo("*") == 0) {
										Log.i(TAG, "single entry val");
										if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(keyToAdd)) {
											hashMapNodes.put(keyToAdd, valueToAdd);
										} else {
											hashMapNodes.remove(keyToAdd);
											hashMapNodes.put(keyToAdd, valueToAdd);
										}
									} else {
										Log.i(TAG, "multiple entries val");
										String strO = "{" + typeOps ;
										String value = null;
										String[] valArr = valueToAdd.split("=");
										for(int i = 0; i < valArr.length; i++) {
											if(valArr[i].compareTo(strO) == 0) {
												Log.i(TAG, "value not found");
											} else {
												Log.i(TAG, "value found as: " + valArr[i].substring(0,32));
												value = valArr[i].substring(0,32);
											}
										}
										if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(keyToAdd)) {
											hashMapNodes.put(keyToAdd, value);
										} else {
											hashMapNodes.remove(keyToAdd);
											hashMapNodes.put(keyToAdd, value);
										}
									}
								}
								cursor.close();
							} else {
								Log.i(TAG, "cursor fetched no rows");
							}
						} catch (Exception e) {
							Log.e(TAG, "Exception at cursor operation");
							e.printStackTrace();
						}
						String strToSend = "{";
						Iterator<String> iter = listOfNodes.iterator();
						while(iter.hasNext()) {
							String key = iter.next();
							String val = null;
							if(hashMapNodes.isEmpty() || !hashMapNodes.containsKey(key)) {
								Log.i(TAG, "no entry found");
							} else {
								val = hashMapNodes.get(key);
								Log.i(TAG, "entry found as: " + val);
							}
							strToSend = strToSend + key + "=" + val + ",";
						}
						if(strToSend.substring(strToSend.length()-1).compareTo(",") == 0) {
							strToSend = strToSend.substring(0, strToSend.length() - 1) + "}";
						} else {
							strToSend = strToSend + "}";
						}
						Log.i(TAG, "string to be sent: " + strToSend);
						dataOut.writeUTF(strToSend);
						dataOut.flush();
					} else {
						Log.i(TAG, "invalid operation");
					}
					dataOut.flush();
					dataOut.close();
					dataIn.close();
					socket.close();
				}
			} catch (NoSuchFieldError e) {
				Log.e(TAG, "NoSuchFieldError: ServerTask");
				e.printStackTrace();
			} catch (Exception e) {
				Log.e(TAG, "Exception: ServerTask");
				e.printStackTrace();
			}
			return null;
		}
	}

	//using code from pa3
	private class ClientTask extends AsyncTask<String, String, String> {

		@Override
		protected synchronized String doInBackground(String... strings) {

			Socket clientSocket = null;
			Log.i(TAG, "Received string: " + strings[0]);
			String[] inStr = strings[0].split(":");
			String optype = inStr[0];
			int type = getOperationType(optype);
			int destPort = 0;
			DataOutputStream dataOut = null;
			DataInputStream dataIn = null;
			String inMessage = "COMPLETE";

			try {
				while(isRejoining == 1) {
					///Log.i(TAG, "do nothing, wait till serverTask doInBackground completes");
				}
				destPort = Integer.parseInt(inStr[1]) * 2;
				clientSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), destPort);
				dataOut = new DataOutputStream(clientSocket.getOutputStream());
				Log.i(TAG, "Msg to write: " + strings[0]);
				dataOut.writeUTF(strings[0]);
				dataOut.flush();
				clientSocket.setSoTimeout(1000);
				dataIn = new DataInputStream(clientSocket.getInputStream());
				try {
					inMessage = dataIn.readUTF();
				} catch (IOException e) {
					Log.e(TAG, "IOException: ClientTask");
					e.printStackTrace();
					inMessage = "ERROR";
				}
				clientSocket.close();
				if(inMessage.compareTo("ERROR") == 0) {
					Log.i(TAG, "error reading msg to client");
					Log.e(TAG, "error reading msg to client");
				} else if(inMessage.compareTo("COMPLETE") == 0) {
					Log.i(TAG, "do nothing!");
				} else {
					Log.i(TAG, "message received from server as: " + inMessage);
					return inMessage;
				}
			} catch (SocketTimeoutException e) {
				Log.e(TAG, "SocketTimeoutException: ClientTask");
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(TAG, "IOException: ClientTask");
				e.printStackTrace();
			} catch (NullPointerException e) {
				Log.e(TAG, "NullPointerException: ClientTask");
				e.printStackTrace();
			} catch (NoSuchFieldError e) {
				Log.e(TAG, "NoSuchFieldError: ClientTask");
				e.printStackTrace();
			} catch (Exception e) {
				Log.e(TAG, "Exception: ClientTask");
				e.printStackTrace();
			}
			return null;
		}
	}
}