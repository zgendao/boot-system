(def project 'template)
(def version "0.1.1")

(set-env!
 :repositories #(conj % '["bintray" {:url "https://dl.bintray.com/nitram509/jbrotli"}]))

(set-env!
 :resource-paths #{"resources"}
 :source-paths   #{"src/clj" "src/cljs"}
 :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                 [adzerk/boot-reload "0.5.2" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                 [nha/boot-uglify "0.0.6"]

                 [org.clojure/clojure       "1.10.0"]
                 [org.clojure/clojurescript "1.9.946"]
                            
                 [org.clojure/java.jdbc "0.7.3"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [clj-commons/fs "1.5.0"]

                 [environ "1.1.0"]
                 [boot-environ "1.1.0"]
                 [org.danielsz/system "0.4.2-SNAPSHOT"]
                 [org.clojure/tools.nrepl "0.2.13"]

                 [org.clojure/core.async "0.3.443"]
                 [com.taoensso/sente        "1.11.0"] ; <--- Sente
                 [com.taoensso/timbre       "4.10.0"]
                 [com.taoensso/encore "2.91.1"]

                 [brave/zente "0.1.3-SNAPSHOT"]
                 [brave/swords "0.1.0-SNAPSHOT"]

                 [org.immutant/immutant "2.1.9"]
                 ;;; TODO Choose (uncomment) a supported web server -----------------------
                 [http-kit                             "2.2.0"] ; Default
                 ;; [org.immutant/web                  "2.1.4"]
                 ;; [nginx-clojure/nginx-clojure-embed "0.4.4"] ; Needs v0.4.2+
                 ;; [aleph                             "0.4.1"]
                 ;; -----------------------------------------------------------------------

                 [ring                      "1.6.3"]
                 [ring/ring-defaults        "0.3.1"] ; Includes `ring-anti-forgery`, etc.
                 [ring-middleware-format "0.7.2"]

                 [compojure                 "1.6.0"] ; Or routing lib of your choice
                 [hiccup                    "1.0.5"] ; Optional, just for HTML

                 [com.cognitect/transit-clj  "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 
                 [metosin/ring-http-response "0.9.0"]
                            
                 [reagent "0.8.0-alpha2"]
                 [reagi "0.10.1"]
                 [funcool/bide "1.6.0"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [binaryage/devtools "0.9.4" :scope "test"]
                 [weasel "0.7.0" :scope "test"]

                 [differ "0.3.2"]
                 ;[paren-soup "2.14.3"]
                 ;[fipp "0.6.18"]
                 [com.brunobonacci/clj-sophia "0.5.2"]
                 ;[cljsjs/three "0.1.01-1"]
                 [zcaudate/hara.time "2.8.7"]
                 [im.chit/hara.io.scheduler "2.5.10"]
                 ])

(task-options!
 aot {:namespace   #{'template.core}}
 pom {:project     project
      :version     version
      :description ""
      :url ""
      :scm {:url ""}
      :license {"MIT" ""}}
 jar {:main        'template.core
      :file        (str "template-" version "-standalone.jar")})

(require '[system.repl :refer [init start stop go reset]]
         '[template.system :refer [dev-system]]
         '[environ.boot :refer [environ]]
         '[nha.boot-uglify  :refer [uglify]]
         '[system.boot :refer [system run]])

(require '[adzerk.boot-cljs :refer :all]
         '[adzerk.boot-cljs-repl :refer :all]
         '[adzerk.boot-reload :refer :all])

(deftask dev
  []
  (comp
   (environ :env {:http-port "7000"})
   (watch :verbose true)
   (system :sys #'dev-system
           :auto true 
           :files ["system.clj" "server.clj"])
;   (reload :asset-path "public")
;   (cljs-repl)
   (cljs :source-map true :optimizations :none)
   (repl :server true)))

(deftask cljsbuild
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp 
      (cljs :optimizations :simple)
      (target :dir dir)
      )))

(deftask build
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp 
      (cljs :optimizations :advanced)
      ;(uglify)
      (environ :env {:http-port "7000"})
      (aot)
      (pom) 
      (uber)
      (jar) 
      (target :dir dir)
      )))

