package com.central.listener;


import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableScheduling
public class EurekaInstanceCanceledListener implements ApplicationListener {
    private Logger log = LoggerFactory.getLogger(EurekaInstanceCanceledListener.class);
    @Override
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        // 服务挂掉自动通知
        if(applicationEvent instanceof EurekaInstanceCanceledEvent) {
            // 应该设置时长，多少秒后服务没有启动如何
            EurekaInstanceCanceledEvent event = (EurekaInstanceCanceledEvent) applicationEvent;
            PeerAwareInstanceRegistry registry = EurekaServerContextHolder.getInstance().getServerContext().getRegistry();
            Applications applications = registry.getApplications();
            applications.getRegisteredApplications().forEach((registeredApplication) -> {
                registeredApplication.getInstances().forEach((instance) -> {
                    if(instance.getInstanceId().equals(event.getServerId())){
                        log.debug("服务：" + instance.getAppName() + " 挂啦。。。");
                        String id = instance.getInstanceId();
                        lostInstanceMap.remove(id);
                        lostInstanceMap.put(id,new LostInstance(instance));
                    }
                });
            });
//            log.info("服务停机啦。。。");

        }
        if(applicationEvent instanceof EurekaInstanceRegisteredEvent){
//            log.info("服务注册成功啦。。。");
            EurekaInstanceRegisteredEvent event = (EurekaInstanceRegisteredEvent) applicationEvent;
            log.debug("服务：" + event.getInstanceInfo().getAppName() + " 注册成功啦。。。");
            lostInstanceMap.remove(event.getInstanceInfo().getInstanceId());
            System.err.println("服务：" + event.getInstanceInfo().getAppName() + " 注册成功啦。。。");
        }

        if (applicationEvent instanceof EurekaInstanceRenewedEvent) {
            EurekaInstanceRenewedEvent event = (EurekaInstanceRenewedEvent) applicationEvent;
            log.debug("心跳检测服务：" + event.getInstanceInfo().getAppName() + "。。");
            System.err.println("心跳检测服务：" + event.getInstanceInfo().getAppName() + "。。");
        }
        if (applicationEvent instanceof EurekaRegistryAvailableEvent) {
            System.err.println("注册中心 启动");
        }
        if (applicationEvent instanceof EurekaServerStartedEvent) {
            System.err.println("Eureka Server 启动");
        }
    }

    private ConcurrentHashMap<String,LostInstance> lostInstanceMap = new ConcurrentHashMap<String,LostInstance>();
    private int defalutNotifyInterval[] = {0,60,120,240,480};
    @Scheduled(cron = "0/30 * * * * ?")
    private void notifyLostInstance(){
        lostInstanceMap.entrySet().forEach((lostInstanceMap)->{
            String key = lostInstanceMap.getKey();
            LostInstance lostInstance = lostInstanceMap.getValue();
            DateTime dt = new DateTime(lostInstance.getLostTime());
            if(dt.plusSeconds(defalutNotifyInterval[lostInstance.getCurrentInterval()]).isBeforeNow()){
                log.info("服务：{}已失效，IP为：{}，失效时间为：{}，请马上重启服务！",new Object[]{lostInstance.getInstanceId(),lostInstance.getIPAddr(),dt.toString()});
                // TODO: 2017/7/8 增加消息提醒
            }
        });
    }

    class LostInstance extends InstanceInfo{
        protected int currentInterval = 0;
        protected Date lostTime;
        public LostInstance(InstanceInfo ii) {
            super(ii);
            this.lostTime = new Date();
        }
        public Date getLostTime() {
            return lostTime;
        }
        public void setLostTime(Date lostTime) {
            this.lostTime = lostTime;
        }
        public int getCurrentInterval(){
            return currentInterval++%4;
        }
    }
}
