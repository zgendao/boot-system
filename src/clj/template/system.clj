(ns template.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [hara.io.scheduler :as hara]
            [com.brunobonacci.sophia :as sph]
            [differ.core :as differ]

            [template.routes :refer [site]]
            
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            
            [system.repl :refer [system]]

            (ring.middleware
              [defaults :refer [site-defaults api-defaults wrap-defaults]]
              [params :refer [wrap-params]]
              [keyword-params :refer [wrap-keyword-params]]
              [format :refer [wrap-restful-format]]
              [resource :refer [wrap-resource]]
              [not-modified :refer [wrap-not-modified]]
              [content-type :refer [wrap-content-type]]
              ;[multipart-params :refer [wrap-multipart-params]]
              ;[gzip :refer [wrap-gzip]]
              ;[cors :refer [wrap-cors]]
              ;[session :refer [wrap-session]]
              )


            (system.components
             [http-kit :refer [new-web-server]]
             [endpoint :refer [new-endpoint]]
             [middleware :refer [new-middleware]]
             [repl-server :refer [new-repl-server]]
             [sente :refer [new-channel-socket-server sente-routes]]
             [handler :refer [new-handler]])))


(defrecord Sophia [path dbs] component/Lifecycle
    (start [component] (assoc component :db (sph/sophia {:sophia.path path :dbs dbs})))
    (stop [component] (dissoc component :db)))

(defn new-db [path dbs] (map->Sophia {:path path :dbs dbs}))


(defrecord Cache [] component/Lifecycle
    (start [component] (assoc component :clients (atom {}) :operations (atom {})))
    (stop [component] (dissoc component :clients :operations)))

(defn new-cache [] (map->Cache {}))


(defrecord Scheduler [schedulers] component/Lifecycle
    (start [component] 
      (let [scheduler 
            (hara/scheduler
              schedulers
              {}
              {:clock {:type "clojure.lang.PersistentArrayMap"
                       :timezone "CET"
                       :interval 1
                       :truncate :millisecond}})]
        (hara/start! scheduler) 
        (assoc component :scheduler scheduler)))
    (stop [component] 
      (hara/stop! (:scheduler component)) 
      (dissoc component :scheduler)))

(defn new-scheduler
    ([] (new-scheduler (hara/scheduler {})))
    ([schedulers] (map->Scheduler {:schedulers schedulers})))

(defn chsk-handler
  [{network-id :uid :as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [{:keys [session]} ring-req
        {:keys []} session
        client-id (get-in ring-req [:params :client-id])
        ]
    (case (name id)
      "uidport-open"    (println "Open")
      "uidport-close"   (swap! (get-in system [:cache :clients]) update client-id disj network-id) 
      "ws-ping"         nil
      nil
      )))

;Create separated Sophia table for each client
;Write out only the diffs and update the whole in cache
;aggregate operations and send out data to clients only at every 60ms

(defn deep-merge [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))

(defn data-handler
  [{network-id :uid :as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [{:keys [session]} ring-req
        {:keys []} session
        client-id (get-in ring-req [:params :client-id])
        sophia (get-in system [:sophia :db])
        ]
    (case (name id)
    
      "reset" (sph/set-value! sophia "state" client-id ?data)
      
      "patch"
      (sph/transact!
        sophia
        (fn [tx]
          (let [u (sph/get-value tx "state" client-id)]
            (when u (sph/set-value! tx "state" client-id (differ/patch u ?data))))))
       
      "merge"
      (sph/transact!
        sophia
        (fn [tx]
          (let [u (sph/get-value tx "state" client-id)]
            (when u (sph/set-value! tx "state" client-id (merge u ?data))))))

      "deepmerge"
      (sph/transact!
        sophia
        (fn [tx]
          (let [u (sph/get-value tx "state" client-id)]
            (when u (sph/set-value! tx "state" client-id (deep-merge u ?data))))))
    
      "assoc"
      (sph/transact!
        sophia
        (fn [tx]
          (let [u (sph/get-value tx "state" client-id)]
            (when u (sph/set-value! tx "state" client-id (assoc u (first ?data) (second ?data)))))))

      "associn" 
      (sph/transact!
        sophia
        (fn [tx]
          (let [u (sph/get-value tx "state" client-id)]
            (when u (sph/set-value! tx "state" client-id (assoc-in u (first ?data) (second ?data)))))))

      "dissoc" (fn [k] (send-fn [:data/dissoc k]))
      (sph/transact!
        sophia
        (fn [tx]
          (let [u (sph/get-value tx "state" client-id)]
            (when u (sph/set-value! tx "state" client-id (dissoc u ?data))))))

      nil
      )
   (doseq [listener (get @(get-in system [:cache :clients]) client-id)]
     (send-fn listener [id ?data]) 
     ) 
   ))

(defn dev-system []
  (component/system-map

    :cache (new-cache)

    :sophia (new-db "database" ["counters" "state"])

    :sente (component/using 
             (new-channel-socket-server 
               (fn [component]
                 (fn [{id :id ?reply-fn :?reply-fn :as ev-msg}]
                 (case (namespace id)
                   "chsk" (chsk-handler ev-msg)
                   "data" (data-handler ev-msg)
                   (do
                     (println "Unhandled event:" ev-msg)
                     (when ?reply-fn (?reply-fn {:umatched-event-as-echoed-from-from-server ev-msg}))))))
               (get-sch-adapter)
               {:user-id-fn 
                (fn [req] 
                  (let [cache (get-in system [:cache :clients])
                        cid (:client-id req)
                        uid (str (java.util.UUID/randomUUID))]
                    (swap! cache assoc cid (conj (or (get @cache cid) #{}) uid))
                    uid))
                :handshake-data-fn
                 (fn [ring-req]
                   (let [client-id (get-in ring-req [:params :client-id])
                         known? (sph/get-value (get-in system [:sophia :db]) "state" client-id)]
                     (println "Client connecting.." client-id)
                     (when-not known? 
                       (do
                         (println "Initializing db..")
                         (sph/set-value! (get-in system [:sophia :db]) "state" client-id {})))
                     (or
                       known?
                       {})
                     ))
                :wrap-component? true})
             [:sophia])
   
    :sente-middleware (new-middleware {:middleware [wrap-params wrap-keyword-params]})
    
    :sente-endpoint (component/using (new-endpoint sente-routes) [:sente :site-middleware])

    :broadcaster (new-scheduler
                   {:report 
                    {:handler 
                     (fn [t]
                       (println 
                         (dissoc t 
                                 :runtime
                                 :type))
                       ;(println (:any @(get-in system [:sente :connected-uids])))
                       (println "Active clients:" @(get-in system [:cache :clients]))) 
                     :schedule "/60 * * * * * *"}
                    
                    })

    :site-middleware (new-middleware {:middleware [[wrap-defaults site-defaults]]})
    
    :site-endpoint (component/using (new-endpoint site) [:site-middleware])

    :handler (component/using (new-handler) [:sente-endpoint :site-endpoint])

    :server (component/using (new-web-server 7000) [:handler])

    ))

(defn prod-system [] (dev-system))


