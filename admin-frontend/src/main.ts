import { createApp } from 'vue';
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import App from './App.vue';
import { router } from './app/router';
import { pinia } from './stores';
import './styles/global.css';

createApp(App).use(Antd).use(pinia).use(router).mount('#app');
