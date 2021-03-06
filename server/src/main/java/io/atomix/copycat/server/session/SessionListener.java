/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.copycat.server.session;

import io.atomix.copycat.client.session.Session;

/**
 * Support for listening for state changes in server sessions.
 * <p>
 * When implemented by a {@link io.atomix.copycat.server.StateMachine StateMachine}, this interface provides
 * support to state machines for reacting to changes in the sessions connected to the cluster. State machines
 * can react to clients {@link #register(Session) registering} and {@link #unregister(Session) unregistering}
 * sessions and servers {@link #expire(Session) expiring} sessions.
 * <p>
 * {@link Session}s represent a single client's open connection to a cluster. Within the context of a session,
 * Copycat provides additional guarantees for clients like linearizability for writes and sequential consistency
 * for reads. Additionally, state machines can push messages to specific clients via sessions. Typically, all
 * state machines that rely on session-based messaging should implement this interface to track when a session
 * is {@link #close(Session) closed}.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public interface SessionListener {

  /**
   * Called when a new session is registered.
   * <p>
   * A session is registered when a new client connects to the cluster or an existing client recovers its
   * session after being partitioned from the cluster. It's important to note that when this method is called,
   * the {@link Session} is <em>not yet open</em> and so events cannot be {@link Session#publish(String, Object) published}
   * to the registered session. This is because clients cannot reliably track messages pushed from server state machines
   * to the client until the session has been fully registered. Session event messages may still be published to
   * other already-registered sessions in reaction to a session being registered.
   * <p>
   * To push session event messages to a client through its session upon registration, state machines can
   * use an asynchronous callback or schedule a callback to send a message.
   * <pre>
   *   {@code
   *   public void register(Session session) {
   *     executor.execute(() -> session.publish("foo", "Hello world!"));
   *   }
   *   }
   * </pre>
   * Sending a session event message in an asynchronous callback allows the server time to register the session
   * and notify the client before the event message is sent. Published event messages sent via this method will
   * be sent the next time an operation is applied to the state machine.
   *
   * @param session The session that was registered.
   */
  void register(Session session);

  /**
   * Called when a session is unregistered by the client.
   * <p>
   * This method is called only when a client explicitly unregisters its session by closing it. In other words,
   * calls to this method indicate that the session was closed by the client rather than {@link #expire(Session) expired}
   * by a server. This method will always be called for a given session before {@link #close(Session)}, and
   * {@link #close(Session)} will always be called following this method.
   * <p>
   * State machines are free to {@link Session#publish(String, Object)} session event messages to any session except
   * the one being unregistered. Session event messages sent to the session being unregistered will be lost.
   *
   * @param session The session that was unregistered.
   */
  void unregister(Session session);

  /**
   * Called when a session is expired by the system.
   * <p>
   * This method is called when a client fails to keep its session alive with the cluster. If the leader hasn't heard
   * from a client for a configurable time interval, the leader will expire the session to free the related memory.
   * This method will always be called for a given session before {@link #close(Session)}, and {@link #close(Session)}
   * will always be called following this method.
   * <p>
   * State machines are free to {@link Session#publish(String, Object)} session event messages to any session except
   * the one that expired. Session event messages sent to the session that expired will be lost.
   *
   * @param session The session that was expired.
   */
  void expire(Session session);

  /**
   * Called when a session was closed.
   * <p>
   * This method is called after a {@link Session} is either {@link #unregister(Session) unregistered} or
   * {@link #expire(Session) expired}. State machines can implement this method to react to any session being
   * removed from memory. This method will always be called for a specific session after either
   * {@link #unregister(Session)} or {@link #expire(Session)}, and one of those methods will always be called
   * before this method.
   *
   * @param session The session that was closed.
   */
  void close(Session session);

}
