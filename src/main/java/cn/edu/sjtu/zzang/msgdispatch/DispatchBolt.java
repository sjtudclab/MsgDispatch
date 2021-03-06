package cn.edu.sjtu.zzang.msgdispatch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import cn.edu.sjtu.se.dclab.auth.thrift.AuthClient;
import cn.edu.sjtu.se.dclab.oss.thrift.OnlineStatusQueryClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.mongodb.MongoClient;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;

public class DispatchBolt extends BaseRichBolt {

	/**
	 * 
	 */
	private static final long serialVersionUID = 272595194167901485L;
	
	private MqttClient dispatcher;
    private int qos = Conf.RABBITMQ_QOS;
    private AuthClient client;
    private Morphia morphia;
    Datastore datastore;
    OnlineStatusQueryClient OSQclient;
    
	public void execute(Tuple input) {
		// TODO Auto-generated method stub
		String msg = input.getStringByField("msg");
		
		msg_dispatch(msg);
		msg_save(msg);
	}

	public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
		// TODO Auto-generated method stub
		prepare_onlineStatusQueryClient();
		prepare_mongodb();        
        prepare_dispatcher();        
//        prepare_authclient();
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		// TODO Auto-generated method stub
	}
	
	private void prepare_dispatcher() {
		String broker       = Conf.RABBITMQ_BROKER;
        String clientId     = "storm-pusher";
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            dispatcher = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setUserName(Conf.RABBITMQ_USER);
            connOpts.setPassword(Conf.RABBITMQ_PASS);
            dispatcher.connect(connOpts);
            System.out.println("Bolt: Connected");  
        } catch(MqttException me) {
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
        }
	}
	
	private void prepare_authclient() {
		client = new AuthClient();
		client.setNodeName("/authService");
		try {
			client.startClient();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}
	
	private void prepare_mongodb() {			   
		try {
			morphia = new Morphia();
		    // tell morphia where to find your classes
		    // can be called multiple times with different packages or classes
		    morphia.mapPackage("cn.edu.sjtu.se.dclab.morphia");
		    // create the Datastore connecting to the database running on the default port on the local host
		    datastore = morphia.createDatastore(new MongoClient(Conf.MONGODB_HOST, Conf.MONGODB_PORT), Conf.MONGODB_DBNAME);                        
		    datastore.ensureIndexes();
		    System.out.println("Mongodb: Connnected");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}
    
	private void prepare_onlineStatusQueryClient()
	{
		try {
			OSQclient = new OnlineStatusQueryClient();
			System.out.println("OnlineStatusQueryClient connected");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    
    
	private void msg_dispatch(String msg) {

		int fid = 0;
		int tid = 0;
		int type = 0;
		boolean flag = false;
		String content = "";
		String topic = "recv";
		try {
            System.out.println("Bolt message: " + msg);
            
            ObjectMapper mapper = null;
            try {
                mapper = new ObjectMapper();
                Message ms = mapper.readValue(msg, Message.class);
                fid = ms.getFromid();
                tid = ms.getToid();
                type = ms.getType();
            	flag = true;
            	content += ms.getContent();
/*
                if (client.validation(fid, tid, type)) {
                	flag = true;
                	content += ms.getContent();
                } else {
                	flag = false;
                	content += "请先申请添加！";
                }
                */
/*                Map<String, Object> param = new HashMap<String, Object>();
                param.put("from", fid);
                param.put("to", tid);
                param.put("type", type);
                String res = URLUtil.util_post(Conf.RELATION_URL, param).trim();
				if (res.equals("true")) {
					flag = true;
					content += ms.getContent();
				} else {
					flag = false;
					content += "请先申请添加！";
				}*/
			} catch (IOException e) {
				// TODO Auto-generated catch block
				flag = false;
				content += "获取关系失败！";
				e.printStackTrace();
			}
            
            if (type == 1) {
                Message sendingMsg = new Message();
                sendingMsg.setContent(content);
                sendingMsg.setFromid(fid);
                sendingMsg.setToid(tid);
                sendingMsg.setType(1);
                String sendingMsgStr = "";
                try {
  				    sendingMsgStr = mapper.writeValueAsString(sendingMsg);
  			    } catch (JsonProcessingException e) {
  				    e.printStackTrace();
  			    }
              
                MqttMessage message = new MqttMessage(sendingMsgStr.getBytes());
                message.setQos(qos);
                if (flag)
              	    topic += tid;
                else
              	    topic += fid;
                System.out.println("Publishing:" + topic + " " + sendingMsgStr);
                dispatcher.publish(topic, message);
            } else {
            
	            //TODO 单聊不用变，群聊拉取群成员id，循环执行以下段落137-156
				String res = "[]";
				try {
					res = URLUtil.util_get("http://202.120.40.111:8080/community-server/rest/groups/"+tid+"/users", new HashMap<String, Object>()).trim();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				System.out.println(res);
				String subString = res.substring(1, res.length() - 1);
				if (subString.length() == 0)
					return;
				String[] splitStrings = subString.split(",");
				int st = 0;
				for (String string : splitStrings) {
					topic = "recv";
					st = Integer.valueOf(string);
					if (fid == st)
						continue;
		            Message sendingMsg = new Message();
		            sendingMsg.setContent(content);
		            sendingMsg.setFromid(fid);
		            sendingMsg.setToid(tid);
		            sendingMsg.setType(2);
		            String sendingMsgStr = "";
		            try {
						sendingMsgStr = mapper.writeValueAsString(sendingMsg);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
		            
		            MqttMessage message = new MqttMessage(sendingMsgStr.getBytes());
		            message.setQos(qos);
		            if (flag)
		            	topic += st;
		            else
		            	topic += fid;
		            System.out.println("Publishing:" + topic + " " + sendingMsgStr);
		            dispatcher.publish(topic, message);
				}
            }
            
//            Message sendingMsg = new Message();
//            sendingMsg.setContent(content);
//            sendingMsg.setFromid(fid);
//            sendingMsg.setToid(tid);
//            sendingMsg.setType(1);
//            String sendingMsgStr = "";
//            try {
//				sendingMsgStr = mapper.writeValueAsString(sendingMsg);
//			} catch (JsonProcessingException e) {
//				e.printStackTrace();
//			}
//            
//            MqttMessage message = new MqttMessage(sendingMsgStr.getBytes());
//            message.setQos(qos);
//            if (flag)
//            	topic += tid;
//            else
//            	topic += fid;
//            System.out.println("Publishing:" + topic + " " + sendingMsgStr);
//            dispatcher.publish(topic, message);
		} catch (MqttPersistenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void msg_save(String msg) {				
        System.out.println("Bolt message: " + msg);
        ObjectMapper mapper = null;
        try {
            mapper = new ObjectMapper();
            Message mso = mapper.readValue(msg, Message.class);
            cn.edu.sjtu.se.dclab.morphia.Message  ms = new cn.edu.sjtu.se.dclab.morphia.Message(
            		mso.getFromid(),mso.getToid(),mso.getType(), mso.getContent());
            String status = OSQclient.checkOnline(Integer.toString(mso.getToid()));            
            System.out.println(status);
            datastore.save(ms);
            System.out.println("Message save to mongodb");
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	
            
	}
}
