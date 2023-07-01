package com.ytyo.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytyo.Dispatcher.Forward;
import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

//粗糙的实现日志
@RestController
public class LogController {

    public static final List<String> errors = new CopyOnWriteArrayList<>();
    public static final Map<Map<Integer, String>, Map<Integer, ChannelOrder.Event>> eventMaps = new ConcurrentHashMap<>();
    public final static AtomicInteger i = new AtomicInteger();

    @GetMapping("/log")
    public List<String> log() {
        return errors;
    }

    @GetMapping("/logmap")
    public List<String> logMap() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayList<String> list = new ArrayList<>();
        eventMaps.forEach((recordMap, eventMap) -> {
            if (!eventMap.isEmpty()) {
                eventMap.forEach((order, event) -> {
                    if (event.isWrite()) {
                        try {
                            list.add(String.format("""
                                    order:%s,buf:%s,所在channel的累计event的记录map:%s
                                    """, order, event.getBuf().readableBytes(), mapper.writeValueAsString(recordMap).substring(0, 10)));
                        } catch (JsonProcessingException e) {
                            list.add("JsonProcessingException:序列化recordMap失败,错误:"+e.getMessage());
                            return;
                        }
                    }
                });
            }
        });
        return list;
    }

    @GetMapping("/logconn")
    public int logconn() {
        int i1 = i.get();
        i.set(0);
        return i1;
    }

    public static final List<ChannelOrder> tables = new CopyOnWriteArrayList<>();

    @GetMapping("/lognowconn")
    public List<String> lognowconn() {
        ArrayList<String> list = new ArrayList<>();
        tables.forEach(t -> {
            list.add("kv:" + t.keyValueMap.size());
            list.add("vk:" + t.valueKeyMap.size());
        });
        return list;
    }

    @GetMapping("/logtestapply")
    public int logtestapply() {
        return Forward.testReply.get();
    }
}
