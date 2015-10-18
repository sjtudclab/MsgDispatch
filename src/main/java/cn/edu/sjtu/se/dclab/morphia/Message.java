package cn.edu.sjtu.se.dclab.morphia;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Property;

@Entity("messages")
public class Message {	
	@Id
    private ObjectId id;
	
	@Property("from")
	private int fromId;
	@Property("to")
	private int toId;
	@Property("type")
	private int type;
	@Property("content")
	private String content;
	// 0: unread 
	// 1: read
	@Property("status")
	private int status;
	
	public Message() {
    }

	public Message(int fromId, int toId, int type, String content) {
		super();
		this.fromId = fromId;
		this.toId = toId;
		this.type = type;
		this.content = content;
	}

	public ObjectId getId() {
		return id;
	}
	public void setId(ObjectId id) {
		this.id = id;
	}
	public int getFromId() {
		return fromId;
	}
	public void setFromId(int fromId) {
		this.fromId = fromId;
	}
	public int getToId() {
		return toId;
	}
	public void setToId(int toId) {
		this.toId = toId;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	
}
