package org.example.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageBus {
    private final Map<QuanTecPlugin, List<String>> plugins;

    public MessageBus(){
        this.plugins = new HashMap<>();
    }

    public void registerPluginEventSubscriptions(QuanTecPlugin quanTecPlugin, List<String> events){
        this.plugins.put(quanTecPlugin, events);
    }

    public void dispatchEvent(MessageBusEvent event){
        for(Map.Entry<QuanTecPlugin, List<String>> plugin : plugins.entrySet()){
            if(plugin.getValue().contains(event.recipient()) || event.recipient() == null){
                plugin.getKey().onMessageBusEvent(event);
            }
        }
    }
}
