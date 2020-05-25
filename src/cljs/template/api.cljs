(ns template.api
  (:require 
    [reagent.core :as r]
    [brave.zente :as zente]
    [brave.swords :as x]
    [differ.core :as differ]
    ))

(defn edn->json [edn] (.stringify js/JSON (clj->js edn)))
(defn edn->obj [edn] (clj->js edn))


(defn ^:export init
  [args]
  (let [channel (atom nil)
        router (atom nil)
        sync (atom nil)
        database (r/atom nil)
        
        {:keys [apikey host out protocol react] :as a}
        (if (map? args)
          args
          (js->clj args :keywordize-keys true))
        
        _ (js/console.log (str "Connecting to db: "apikey))

        {:keys [chsk ch-recv send-fn state] :as channel-socket}
        (zente/make-channel-socket-client!
          "/chsk"
          nil 
          {:client-id apikey
           :host   host
           :protocol protocol
           :type   :auto
           :packer :edn}
          )
        
        out (case out
              "json" edn->json
              "edn" identity
              "obj" edn->obj
              edn->json)
        ]

    (reset! channel channel-socket)
    
    (when-let [stop! @router] (stop!))

    (reset! router (zente/start-client-chsk-router!
                     ch-recv
                     (fn [{:as ev-msg :keys [id ?data event]}]
                       (case id
                         :chsk/recv 
                         (let [[operation data] ?data
                               new-state
                               (case operation
                                 :data/reset (reset! database data)
                                 :data/patch (reset! database (differ/patch @database data))
                                 :data/merge (swap! database merge data)
                                 :data/deepmerge (swap! database x/deep-merge data)
                                 :data/assoc (swap! database assoc (first data) (second data))
                                 :data/associn (swap! database assoc-in (first data) (second data))
                                 :data/dissoc (swap! database assoc data)
                                 :chsk/ws-ping nil
                                 (js/console.log (str "Unknown " operation)))]
                           (when react (react (out new-state))))
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
    
      {:raw database
       :whole (fn [] (out @database))
       
       :reset (fn [data] (send-fn [:data/reset (js->clj data)]))
       
       :merge (fn [data] (send-fn [:data/merge (js->clj data)]))
       :deepmerge (fn [data] (send-fn [:data/deepmerge (js->clj data)]))
    
       :assoc (fn [k data] (send-fn [:data/assoc [k (js->clj data)]]))
       :associn (fn [k data] (send-fn [:data/associn [(js->clj k) (js->clj data)]]))
       
       :dissoc (fn [k] (send-fn [:data/dissoc k]))
       
       :get (fn [& path] (out (get-in @database (vec path))))
       :getin (fn [path] (out (get-in @database (vec path))))
       }
    ))


