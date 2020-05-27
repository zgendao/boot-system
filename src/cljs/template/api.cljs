(ns template.api
  (:require 
    [reagent.core :as r]
    [brave.zente :as zente]
    [brave.swords :as x]
    [differ.core :as differ]
    ))

(defn init
  [args]
  (let [channel (atom nil)
        router (atom nil)
        sync (atom nil)
        database (r/atom nil)
        
        {:keys [apikey host out protocol react] :as a} args
        
        _ (js/console.log (str "Connecting to db: "apikey))

        {:keys [chsk ch-recv send-fn state] :as channel-socket}
        (zente/make-channel-socket-client!
          "/chsk"
          nil 
          {:client-id apikey
           :host   host
           :protocol protocol
           :type   :auto
           :packer :edn})]

    (reset! channel channel-socket)
    
    (when-let [stop! @router] (stop!))

    (reset! router (zente/start-client-chsk-router!
                     ch-recv
                     (fn [{:as ev-msg :keys [id ?data event]}]
                       (case id
                         :chsk/recv 
                         (let [[operation data] ?data]
                           (case operation
                                 :data/patch (reset! database (differ/patch @database data))
                                 :chsk/ws-ping nil
                                 (js/console.log (str "Unknown " operation))))
                         :chsk/state (js/console.log "State!")
                         :chsk/handshake (reset! database (nth ?data 2))
                         (js/console.log "Not found"))
                       )))
    (add-watch 
      database :watcher
      (fn [key atom old-state new-state]
        (let [difference (differ/diff old-state new-state)]
          (when (not= difference [{} {}]) 
              (send-fn [:data/patch difference])
            ))))
    
    database))


