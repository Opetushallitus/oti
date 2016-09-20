(ns oti.component.http-kit-server
  (:require [com.stuartsierra.component :as component]
            [suspendable.core :as suspendable]
            [org.httpkit.server :as http]))

(defrecord HttpKitServer [app]
  component/Lifecycle
  (start [component]
    (if (:server component)
      component
      (let [options (-> component (dissoc :app))
            handler (atom (delay (:handler app)))
            stop-server  (http/run-server (fn [req] (@@handler req)) options)]
        (assoc component
          :stop-server stop-server
          :handler handler))))
  (stop [component]
    (if-let [stop-server (:stop-server component)]
      (do (stop-server)
          (dissoc component :server :handler))
      component))
  suspendable/Suspendable
  (suspend [component]
    (if-let [handler (:handler component)]
      (do (reset! handler (promise))
          (assoc component :suspended? true))
      component))
  (resume [component old-component]
    (if (and (:suspended? old-component)
             (= (dissoc component :suspended? :server :handler :app)
                (dissoc old-component :suspended? :server :handler :app)))
      (let [handler (:handler old-component)]
        (deliver @handler (:handler app))
        (-> component
            (assoc :server (:server old-component), :handler handler)
            (dissoc :suspended?)))
      (do (when old-component (component/stop old-component))
          (component/start component)))))

(defn http-kit-server
  "Create a HTTP Kit server component from a map of options. The component expects
  an :app key that contains a map or record with a :handler key. This allows
  the Ring handler to be supplied in a dependent component.

  All other options are passed to the HTTP Kit server."
  [options]
  (map->HttpKitServer options))
