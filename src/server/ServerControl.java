package server;

import client.model.FriendsList;
import client.model.Users;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;

/**
 *
 * @author Lenovo
 */
public class ServerControl {

    private int port = 101;
    private ServerSocket serverSocket;
    private DBAccess db;
    HashMap<String, Handler> clientMap;
    Object lock;
    ArrayList<Pair<Handler,Handler>>pairs;
    public ServerControl() {
        lock = new Object();
        db = new DBAccess();
        openConnection();
        clientMap = new HashMap<>();
        pairs=new ArrayList<>();
        while (true) {
            listening();
        }
    }

    public void openConnection() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException ex) {
            Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void closeConnection() {
        try {
            serverSocket.close();
        } catch (IOException ex) {
            Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void listening() {
        try {
            Socket client = serverSocket.accept();
            ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
            String rc = ois.readUTF();
            Users u = (Users) ois.readObject();
            if (rc.equals("login")) {
                if (db.checkUser(u)) {

                    Handler handler = new Handler(u, lock, ois, oos);
                    handler.setSocket(client);
                    clientMap.put(u.getHoten(), handler);

                    oos.writeUTF("Login Successfully");
                    oos.writeObject(u);
                    oos.flush();

                    handler.start();

                    updateOnlineUsers();
                } else {
                    oos.writeUTF("Login Fail");
                    oos.flush();
                }
            } else if (rc.equals("signup")) {
                if (!db.checkUserExist(u)) {
                    oos.writeObject("Signup Successfully");
                    oos.flush();
                } else {
                    oos.writeObject("Signup Fail");
                    oos.flush();
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void updateOnlineUsers() {
        try {
            for (Map.Entry<String, Handler> entry : clientMap.entrySet()) {
                Handler value = entry.getValue();
                value.getOos().writeUTF("online user");
                FriendsList fl = new FriendsList(value.getUser());
                fl.setLf(db.listFr(value.getUser()));
                value.getOos().writeObject(fl);
                value.getOos().flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class Handler extends Thread {

        Object lock;
        ObjectInputStream ois;
        ObjectOutputStream oos;
        Socket socket;
        Users user;

        public Handler(Users user, Object lock, ObjectInputStream ois, ObjectOutputStream oos) {
            this.user = user;
            this.lock = lock;
            this.oos = oos;
            this.ois = ois;
        }

        public void setSocket(Socket socket) {
            this.socket = socket;
        }

        public ObjectInputStream getOis() {
            return ois;
        }

        public ObjectOutputStream getOos() {
            return oos;
        }

        public Users getUser() {
            return user;
        }

        private void closeSocket() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    String rq = ois.readUTF();
                    System.out.println(rq);
                    if (rq.equals("log out")) {
                        Users u = (Users) ois.readObject();
                        db.logOut(u);
                        clientMap.remove(u.getHoten());
                        updateOnlineUsers();
                        break;
                    } 
                    
                    else if (rq.equals("add friend")) {
                        Users thisu = (Users) ois.readObject();
                        Users u = (Users) ois.readObject();
                        if (db.checkUserExist2(u)) {
                            if (db.addFriend(thisu, u)) {
                                synchronized (lock) {
                                    clientMap.get(thisu.getHoten()).getOos().writeUTF("Add friend successfully");
                                }
                                updateOnlineUsers();
                            } else {
                                synchronized (lock) {
                                    clientMap.get(thisu.getHoten()).getOos().writeUTF("Add friend fail");
                                }
                                updateOnlineUsers();
                            }
                        } else {
                            synchronized (lock) {
                                clientMap.get(thisu.getHoten()).getOos().writeUTF("Value doesn't exist");
                            }
                            updateOnlineUsers();
                        }
                    } else if(rq.equals("challenge")){
                        synchronized(lock){
                            Users thisu = (Users) ois.readObject();
                            Users u = (Users) ois.readObject();
//                            clientMap.get(thisu.getHoten()).getOos().writeUTF("challenge");
                            clientMap.get(u.getHoten()).getOos().writeUTF("challenge");
                            clientMap.get(u.getHoten()).getOos().writeObject(thisu);
                            clientMap.get(u.getHoten()).getOos().writeObject(u);
                        }
//                        updateOnlineUsers();
                    } 
                    
                    else if(rq.equals("accept")){
                        synchronized(lock){
                            Users thisu = (Users) ois.readObject();
                            Users u = (Users) ois.readObject();
                            clientMap.get(thisu.getHoten()).getOos().writeUTF("accept");
                            clientMap.get(thisu.getHoten()).getOos().writeObject(u);
                            clientMap.get(u.getHoten()).getOos().writeUTF("accept");
                            clientMap.get(u.getHoten()).getOos().writeObject(thisu);
                            pairs.add(new Pair<>(clientMap.get(thisu.getHoten()),clientMap.get(u.getHoten())));
                        }
                        //updateOnlineUsers();
                    }
                    else if(rq.equals("not accept")){
                        synchronized(lock){
                            Users thisu = (Users) ois.readObject();
                            Users u = (Users) ois.readObject();
                            clientMap.get(thisu.getHoten()).getOos().writeUTF("not accept");
                            clientMap.get(thisu.getHoten()).getOos().writeObject(u);
//                            clientMap.get(u.getHoten()).getOos().writeUTF("not accept");
//                            clientMap.get(u.getHoten()).getOos().writeObject(thisu);
                        }
                        //updateOnlineUsers();
                    }
                    else if(rq.equals("Calculate")){
                        synchronized(lock){
                            Users user = (Users) ois.readObject();
                            Handler temp=clientMap.get(user.getHoten());
                            Pair<Handler,Handler> temp_pair =null;
                            for(Pair<Handler,Handler>i:pairs){
                                if(i.getKey().getUser().getHoten().equals(temp.getUser().getHoten())){
                                    i.getKey().getUser().setFi_time(user.getFi_time());
                                    temp_pair = i;
                                    i.getKey().getUser().setCheck(1);
                                    System.out.println("1");
                                    break;
                                }
                                if(i.getValue().getUser().getHoten().equals(temp.getUser().getHoten())){
                                    i.getValue().getUser().setFi_time(user.getFi_time());
                                    temp_pair = i;
                                    i.getValue().getUser().setCheck(1);
                                    System.out.println("2");
                                    break;
                                }
                            }
//                            if(check==1){
//                                temp.getOos().writeUTF("Wait");
//                                updateOnlineUsers();
//                            }
                                      
                            System.out.println(temp_pair.getKey().getUser().getCheck());
                            System.out.println(temp_pair.getValue().getUser().getCheck());  
                            if(temp_pair.getKey().getUser().getCheck()==1&&temp_pair.getValue().getUser().getCheck()==1){
                                long t1=temp_pair.getKey().getUser().getFi_time();
                                long t2=temp_pair.getValue().getUser().getFi_time();
                                System.out.println("vao");
                                if(t1<t2){
                                    temp_pair.getKey().oos.writeUTF("result");
                                    temp_pair.getKey().oos.writeObject("YOU WIN");
                                    temp_pair.getValue().oos.writeUTF("result");
                                    temp_pair.getValue().oos.writeObject("YOU LOSE");
                                    db.updatePoints(temp_pair.getKey().getUser(), 1);
                                    
                                    
                                    System.out.println(temp_pair.getKey().getUser().getHoten()+"win");
                                }
                                else if(t1>t2){
                                    temp_pair.getKey().oos.writeUTF("result");
                                    temp_pair.getKey().oos.writeObject("YOU LOSE");
                                    temp_pair.getValue().oos.writeUTF("result");
                                    temp_pair.getValue().oos.writeObject("YOU WIN");
                                    db.updatePoints(temp_pair.getValue().getUser(), 1);
                                    System.out.println(temp_pair.getKey().getUser().getHoten()+"lose");
                                    //System.out.println("lose");
                                }
                                else{
                                    temp_pair.getKey().oos.writeUTF("result");
                                    temp_pair.getKey().oos.writeObject("TIE");
                                    temp_pair.getValue().oos.writeUTF("result");
                                    temp_pair.getValue().oos.writeObject("TIE");
                                    db.updatePoints(temp_pair.getKey().getUser(), (float) 0.5);
                                    db.updatePoints(temp_pair.getValue().getUser(), (float) 0.5);
                                    //
                                }
                                temp_pair.getKey().getUser().setFi_time(-1);
                                temp_pair.getValue().getUser().setFi_time(-1);
                                temp_pair.getKey().getUser().setCheck(0);
                                temp_pair.getValue().getUser().setCheck(0);
                                pairs.remove(temp_pair);
//                                System.out.println(pairs.size());
                            }
                            
                        }
                        
                    }
                } catch (IOException ex) {
                    Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(ServerControl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

    }
}
