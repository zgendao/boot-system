(ns template.app
  (:require 
    [reagent.core :as r]
    [template.api :as api]
    [bide.core :as b]))

(enable-console-print!)

(def app-state (api/init {:apikey "test"}))

(defn home-page [app-state]
  [:div "Home 2"
   [:p (str @app-state)]
   [:a {:on-click #(swap! app-state assoc "counter" (rand-int 1000))} "Assoc"]
   ])

(defn about-page []
  [:div "About"])

(defn transform-map [rmap]
  (into []
        (for [[r u] rmap]
          [u r])))

(defn make-bide-router [rmap]
  (b/router (transform-map rmap)))

(def route-map {:template.home "/home"
                :template.about "/about"})

(def router (make-bide-router route-map))

(defn nav-link [route text]
  [:a {:href (route route-map)
       :on-click #(do
                    (-> % .preventDefault)
                    (b/navigate! router route))} text])

(defn page [name app-state]
  [:div
   [:nav
    [:div [nav-link :template.home "Home"]]
    [:div [nav-link :template.about "About"]]]
   (case name
     :template.home (home-page app-state)
     :template.about [about-page])])

(defn ^:export render [name]
  (r/render [page name app-state] (js/document.getElementById "app")))

(defn ^:export navigate
  "A function which will be called on each route change."
  [name params query]
  (println "Route change to: " name params query)
  (render name))

(defn ^:export init []
  (println "Loading..")
  (b/start! router {:default :template.home
                    :html5? true
                    :on-navigate (fn [name params query] (navigate name params query))}))
