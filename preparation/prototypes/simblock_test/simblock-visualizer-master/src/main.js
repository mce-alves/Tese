import Vue from "vue";
import "@/plugins/vuetify";
import App from "./App.vue";

Vue.config.productionTip = false;

window.speed = 3;

window.setSpeed = function(x) {
  window.speed = x;
  console.log("Playback speed set to "+x);
}

new Vue({
  render: h => h(App)
}).$mount("#app");