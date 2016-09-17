package test;

import org.zeromq.ZMQ;

import java.util.Random;

/**
 * Created by Song on 2016/9/10.
 * 测试发布订阅模式
 */
public class PUB_SUB_Mode {
    public static void main(String [] args) throws InterruptedException{
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket publisher = context.socket(ZMQ.PUB);

        publisher.bind("tcp://*:5555");
        publisher.bind("ipc://weather");

        Random random = new Random(System.currentTimeMillis());

        while (!Thread.currentThread().isInterrupted()){
            int temprature = random.nextInt(30);
            String message = String.format("the current temperature is %d 度",temprature);
            System.out.println("成功发送消息:"+message);
            publisher.send("weather".getBytes(),ZMQ.SNDMORE);
            publisher.send(message);
            Thread.sleep(1000);
        }
        publisher.close();
        context.term();
    }
}
