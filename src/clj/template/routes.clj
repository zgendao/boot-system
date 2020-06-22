(ns template.routes
  (:require 
    [compojure.core :refer [routes GET ANY POST]]
    [compojure.route :as route]
    [hiccup.core :as hiccup]
    [ring.util.http-response :as response]))

(defn app [ring-req]
  (hiccup/html
    [:div#app] 
    [:script {:src "js/app.js"}]
    ))

(defn site [_]
  (routes
    (POST "/post-test" ring-req (do (println (:params ring-req)) "Thank you!"))
    (GET "/test" ring-req "Hello world!")
    (GET "/" ring-req (app ring-req))
    (ANY "*" [] (hiccup/html [:h1 {:style {:text-align "center" :color "red"}} "NOT FOUND"]))
    ))
