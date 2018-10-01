#[macro_use]
extern crate kompact;
extern crate protobuf;
extern crate bytes;


use kompact::*;

use ActorPath;
use ActorRef;
//use bytes::{Buf, BufMut};
use ComponentContext;
use Provide;
use default_components::DeadletterBox;
use ControlEvent;
use ControlPort;
use KompicsConfig;
use KompicsSystem;
use NetworkConfig;
use std::net::SocketAddr;
use std::{thread, time};
use std::time::Duration;
use std::env;
use Transport;

use prelude::*;

mod messages;
use messages::*;
use protobuf::Message;

use std::os;



fn main() {
    let args: Vec<String> = env::args().collect();

    if args.len() > 4 {
        let executor_name = &args[1].to_string();
        let task_manager_proxy = &args[2].to_string();
        let task_executor_actor_path = &args[3].to_string();
        let state_manager_proxy = &args[4].to_string();
        let state_master_actor_path = &args[5].to_string();

        println!("Executor Name{}", executor_name);
        println!("TaskManager proxy{}", task_manager_proxy);
        println!("TaskExecutor Actor Path{}", task_executor_actor_path);
        println!("StateManager proxy{}", state_manager_proxy);
        println!("StateMaster Actor Path{}", state_master_actor_path);

        let sm_socket_addr = parse_socket_addr(state_manager_proxy);
        let tm_socket_addr = parse_socket_addr(task_manager_proxy);

        let self_socket_addr = SocketAddr::new("127.0.0.1".parse().unwrap(), 0);

        let mut cfg = KompicsConfig::new();
        cfg.system_components(DeadletterBox::new, move || {
            let net_config = NetworkConfig::new(self_socket_addr);
            NetworkDispatcher::with_config(net_config)
        });

        let system = KompicsSystem::new(cfg);

        let sm_path = ActorPath::Named(NamedPath::with_socket(
            Transport::TCP,
            sm_socket_addr,
            vec!["state_manager_proxy".into()],
        ));

        let tm_path = ActorPath::Named(NamedPath::with_socket(
            Transport::TCP,
            tm_socket_addr,
            vec!["task_manager_proxy".into()],
        ));

        let tm_connection: AkkaConnection = AkkaConnection {akka_actor_path: task_executor_actor_path.to_string(),
            addr_str: task_manager_proxy.to_string(), path: tm_path};


        let sm_connection : AkkaConnection = AkkaConnection {akka_actor_path: state_master_actor_path.to_string(),
            addr_str: state_manager_proxy.to_string(), path: sm_path};


        let executor_named = system.create_and_register(move ||
            ExecutorActor::new(self_socket_addr, sm_connection, tm_connection, executor_name.to_string()));

        system.register_by_alias(&executor_named, executor_name.to_string())
            .await_timeout(Duration::from_millis(1000))
            .expect("Registration never completed.");

        system.start(&executor_named);

        thread::sleep(time::Duration::from_millis(180000));
    }

}

pub struct AkkaConnection {
    pub addr_str: String,
    pub path: ActorPath,
    pub akka_actor_path: String,
}

fn parse_socket_addr(in_string: &str) -> (SocketAddr) {
    let mut splitter = in_string.splitn(2, ':');
    let host = splitter.next().unwrap();
    let port = splitter.next().unwrap().parse::<u16>().unwrap();
    SocketAddr::new(host.parse().unwrap(), port)
}

fn parse_socket_addr_tup(addr: &str) -> (&str, i32) {
    let mut splitter = addr.splitn(2, ':');
    let host = splitter.next().unwrap();
    let port = splitter.next().unwrap().parse::<i32>().unwrap();
    (host, port)
}


impl Serialisable for KompactAkkaEnvelope {
    fn serid(&self) -> u64 {
        serialisation_ids::PBUF
    }
    fn size_hint(&self) -> Option<usize> {
        let bytes = self.write_to_bytes().unwrap();
        Some(bytes.len())
    }
    fn serialise(&self, buf: &mut BufMut) -> Result<(), SerError> {
        let bytes = self.write_to_bytes().unwrap();
        buf.put_slice(&bytes);
        Ok(())
    }
    fn local(self: Box<Self>) -> Result<Box<Any + Send>, Box<Serialisable>> {
        Ok(self)
    }
}

pub struct ProtoSer;


impl Deserialiser<KompactAkkaEnvelope> for ProtoSer {
    fn deserialise(buf: &mut Buf) -> Result<KompactAkkaEnvelope, SerError> {
        let parsed = protobuf::parse_from_bytes(buf.bytes()).unwrap();
        Ok(parsed)
    }
}


#[derive(ComponentDefinition)]
struct ExecutorActor {
    ctx: ComponentContext<ExecutorActor>,
    self_addr: SocketAddr,
    sm_conn: AkkaConnection,
    tm_conn: AkkaConnection,
    alias: String
}

impl ExecutorActor {
    fn new(self_addr: SocketAddr, sm_conn: AkkaConnection, tm_conn: AkkaConnection, alias: String) -> ExecutorActor {
        ExecutorActor {
            ctx: ComponentContext::new(),
            self_addr,
            sm_conn,
            tm_conn,
            alias
        }
    }

}


fn akka_registration(alias: &String, self_addr: &SocketAddr, conn: &AkkaConnection) -> KompactAkkaEnvelope {
    let mut src = KompactAkkaPath::new();
    src.set_ip(self_addr.ip().to_string());
    let i32_port = self_addr.port() as i32;
    src.set_port(i32_port);
    src.set_path(alias.clone());

    let mut dst = KompactAkkaPath::new();
    let (host, port) = parse_socket_addr_tup(&conn.addr_str);
    dst.set_ip(host.to_string());
    dst.set_port(port);
    dst.set_path(conn.akka_actor_path.clone());

    let mut executor_registration = ExecutorRegistration::new();
    executor_registration.set_jobId(String::from("job"));
    executor_registration.set_src(src);
    executor_registration.set_dst(dst);

    let mut envelope = KompactAkkaEnvelope::new();
    let mut akka_msg = KompactAkkaMsg::new();
    akka_msg.set_executorRegistration(executor_registration);
    envelope.set_msg(akka_msg);
    envelope
}

impl Provide<ControlPort> for ExecutorActor {
    fn handle(&mut self, event: ControlEvent) -> () {
        match event {
            ControlEvent::Start => {
                // Register at StateManager
                let sm_envelope = akka_registration(&self.alias, &self.self_addr, &self.sm_conn);
                self.sm_conn.path.tell(sm_envelope, self);

                // Register at TaskManager
                let tm_envelope = akka_registration(&self.alias, &self.self_addr, &self.tm_conn);
                self.tm_conn.path.tell(tm_envelope, self)
            }
            _ => (),
        }
    }

}


impl Actor for ExecutorActor {
    fn receive_local(&mut self, _sender: ActorRef, _msg: Box<Any>) -> () {}

    fn receive_message(&mut self, sender: ActorPath, ser_id: u64, buf: &mut Buf) -> () {
        if ser_id == serialisation_ids::PBUF {
            let r: Result<KompactAkkaEnvelope, SerError> = ProtoSer::deserialise(buf);
            if let Ok(data) = r {
                println!("{}", "Got message!");
                println!("{:?}", data);
                let payload = data.msg.unwrap().payload.unwrap();
                match payload {
                    KompactAkkaMsg_oneof_payload::hello(v) => {
                        println!("{}", v.hey);
                        let mut reply = KompactAkkaMsg::new();
                        let mut hey = Hello::new();
                        hey.set_hey(String::from("hello from rust"));
                        reply.set_hello(hey);
                        let mut envelope = KompactAkkaEnvelope::new();
                        envelope.set_msg(reply);
                        sender.tell(envelope, self)
                    }
                    KompactAkkaMsg_oneof_payload::ask(ask) => {
                        // Refactor
                        let mut hey = Hello::new();
                        hey.set_hey(String::from("my_ask_reply"));
                        let mut response = KompactAkkaMsg::new();
                        response.set_hello(hey);

                        let mut ask_reply= AskReply::new();
                        ask_reply.set_askActor(ask.askActor);
                        ask_reply.set_msg(response);

                        let mut reply_msg = KompactAkkaMsg::new();
                        reply_msg.set_askReply(ask_reply);

                        let mut envelope = KompactAkkaEnvelope::new();
                        envelope.set_msg(reply_msg);
                        sender.tell(envelope, self)
                    }
                    KompactAkkaMsg_oneof_payload::executorRegistration(_) => {

                    }
                    KompactAkkaMsg_oneof_payload::askReply(_) => {

                    }
                }
            }
        } else {
            println!("{}", "What are you sending, you fool!")
        }
    }
}
