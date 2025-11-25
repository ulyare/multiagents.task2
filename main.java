

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentContainer;

import java.io.Serializable;
import java.util.*;

public class main extends Agent {

    // Класс для хранения данных агента
    static class NodeData implements Serializable {
        int id; double value;
        NodeData(int i, double v) { id = i; value = v; }
    }

    // Граф связей и начальные значения
    static class Config {
        static final int TOTAL_AGENTS = 15; // Общее количество агентов
        static final int MAX_STAGES = 80; // Количество итераций алгоритма
        static HashMap<Integer, List<Integer>> links = new HashMap<>(); // Связи между агентами
        static HashMap<Integer, Double> values = new HashMap<>(); // Начальные значения агентов
        static List<int[]> allEdges = new ArrayList<>(); // Все возможные ребра графа
        static List<int[]> droppedEdges = new ArrayList<>(); // Отброшенные ребра на текущем шаге (для вывода в консоль)

        static {
            int[][] linkData = {
                    {1, 2, 5, 7},
                    {2, 1, 3},
                    {3, 2, 4},
                    {4, 3, 5, 6},
                    {5, 1, 4, 10},
                    {6, 4, 7},
                    {7, 1, 6, 8},
                    {8, 7, 9},
                    {9, 8, 10},
                    {10, 5, 9, 11},
                    {11, 10, 12},
                    {12, 11, 13},
                    {13, 12, 14, 15},
                    {14, 13, 15},
                    {15, 14, 13}
            };

            double[] valueData = {35, -7, 5, 34, 7, 80, 11, -15, 42, -23, 18, 29, -8, 51, 67};

            // Заполнение структур данных связей и значений
            for (int i = 0; i < linkData.length; i++) {
                List<Integer> neighborList = new ArrayList<>();
                for (int j = 1; j < linkData[i].length; j++) {
                    neighborList.add(linkData[i][j]);
                }
                links.put(linkData[i][0], neighborList);
                values.put(i + 1, valueData[i]);
            }

            for (int i = 1; i <= TOTAL_AGENTS; i++) {
                for (int j : links.get(i)) {
                    if (i < j) allEdges.add(new int[]{i, j});
                }
            }
        }


        static List<Integer> getNeighbors(int id) { return links.get(id); } // Получить соседей агента
        static double getValue(int id) { return values.get(id); } // Получить начальное значение агента
    }

    // Данные текущего агента
    NodeData data;
    int stage = 0;
    boolean stopFlag = false;
    List<Double> received = new ArrayList<>();

    @Override
    protected void setup() {
        // Инициализация агента при создании
        int id = Integer.parseInt(getLocalName().replace("Агент", ""));
        data = new NodeData(id, Config.getValue(id));

        addBehaviour(new ReceiveBehaviour());
        addBehaviour(new SendBehaviour(this, 3000));
        if (id == 1) addBehaviour(new CollectorBehaviour());
    }

    // Поведение для отправки сообщений
    class SendBehaviour extends TickerBehaviour {
        SendBehaviour(Agent a, long period) { super(a, period); }

        @Override
        protected void onTick() {
            // Проверка флага остановки
            if (stopFlag) { stop(); return; }

            // Проверка достижения максимального количества итераций
            if (++stage > Config.MAX_STAGES) {
                stopFlag = true;
                // Отправка сообщений остановки всем агентам
                ACLMessage stopMsg = new ACLMessage(ACLMessage.CANCEL);
                for (int i = 1; i <= Config.TOTAL_AGENTS; i++)
                    stopMsg.addReceiver(new jade.core.AID("Агент" + i, jade.core.AID.ISLOCALNAME));
                send(stopMsg);
                return;
            }

            received.clear();

            if (data.id == 1) selectDroppedEdges();

            List<Integer> activeNeighbors = getActiveNeighbors();
            sendToNeighbors(activeNeighbors);
            sendStatus();
        }

        // Выбор случайных ребер для отбрасывания
        private void selectDroppedEdges() {
            Config.droppedEdges.clear();
            List<int[]> edges = new ArrayList<>(Config.allEdges);
            Collections.shuffle(edges);
            int numDrop = (int)(Math.random() * 3);
            numDrop = Math.min(numDrop, edges.size());
            for (int i = 0; i < numDrop; i++)
                Config.droppedEdges.add(edges.get(i));
        }

        private List<Integer> getActiveNeighbors() {
            List<Integer> active = new ArrayList<>();
            for (int n : Config.getNeighbors(data.id)) {
                boolean isDropped = false;
                for (int[] edge : Config.droppedEdges) {
                    if ((edge[0] == data.id && edge[1] == n) || (edge[0] == n && edge[1] == data.id)) {
                        isDropped = true; break;
                    }
                }
                if (!isDropped) active.add(n); // Добавляем если ребро активно
            }
            return active;
        }

        // Отправка сообщений соседям
        private void sendToNeighbors(List<Integer> neighbors) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            try {
                msg.setContentObject(new NodeData(data.id, data.value)); // Сериализация данных
                for (int neighbor : neighbors)
                    msg.addReceiver(new jade.core.AID("Агент" + neighbor, jade.core.AID.ISLOCALNAME));
                send(msg);
            } catch (Exception e) {
                System.err.println("Send error for agent " + data.id + ": " + e.getMessage());
            }
        }

        private void sendStatus() {
            ACLMessage statusMsg = new ACLMessage(ACLMessage.REQUEST);
            statusMsg.addReceiver(new jade.core.AID("Агент1", jade.core.AID.ISLOCALNAME));
            statusMsg.setContent(stage + "," + data.id + "," + data.value); // Формат: этап,ID,значение
            send(statusMsg);
        }
    }

    // Поведение для приема сообщений
    class ReceiveBehaviour extends Behaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg != null) {
                try {
                    NodeData otherData = (NodeData) msg.getContentObject();
                    received.add(otherData.value + (Math.random() - 0.5)); // Добавление шума

                    // Обновление значения когда получены все ожидаемые сообщения
                    if (received.size() == getActiveNeighbors().size()) {
                        double sum = data.value;
                        for (double v : received) sum += v;
                        data.value = sum / (1 + received.size()); // Усреднение
                        //data.value = sum / 3;
                        received.clear();
                    }
                } catch (Exception e) {
                    System.err.println("Receive error for agent " + data.id + ": " + e.getMessage());
                }
            } else {
                block(); // Блокировка если нет сообщений
            }
        }

        @Override
        public boolean done() { return false; }
    }

    // Поведение для обработки команды остановки
    class StopBehaviour extends Behaviour {
        @Override
        public void action() {
            // Получение сообщений остановки
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.CANCEL));
            if (msg != null) stopFlag = true;
            else block();
        }
        @Override
        public boolean done() { return false; }
    }


    class CollectorBehaviour extends Behaviour {
        private HashMap<Integer, Double> receivedValues = new HashMap<>();
        private int currentStage = 1;

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            if (msg != null) {
                String[] parts = msg.getContent().split(",");
                int msgStage = Integer.parseInt(parts[0]);
                int id = Integer.parseInt(parts[1]);
                double value = Double.parseDouble(parts[2]);

                if (msgStage == currentStage) {
                    receivedValues.put(id, value);
                    if (receivedValues.size() == Config.TOTAL_AGENTS) {
                        printStageResults(); // Вывод результатов этапа
                        if (currentStage >= Config.MAX_STAGES) calculateRealAverage(); // Расчет в конце
                        receivedValues.clear();
                        currentStage++;
                    }
                }
            } else {
                block();
            }
        }

        // Вывод результатов текущего этапа
        private void printStageResults() {
            System.out.println("ШАГ " + currentStage);
            for (int i = 1; i <= Config.TOTAL_AGENTS; i++)
                System.out.println("Агент " + i + ": " + receivedValues.get(i));

            System.out.print("Убранное ребро: ");
            if (Config.droppedEdges.isEmpty()) System.out.println("-");
            else {
                for (int i = 0; i < Config.droppedEdges.size(); i++) {
                    int[] edge = Config.droppedEdges.get(i);
                    System.out.print("(" + edge[0] + "," + edge[1] + ")");
                    if (i < Config.droppedEdges.size() - 1) System.out.print(", ");
                }
                System.out.println();
            }
        }

        // Расчет реального среднего значения по начальным данным
        private void calculateRealAverage() {
            double sum = 0.0;
            for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
                double val = Config.getValue(i);
                sum += val;
            }
            System.out.println("Реальное среднее арифметическое: " + (sum / Config.TOTAL_AGENTS));
        }

        @Override
        public boolean done() { return false; }
    }

    private List<Integer> getActiveNeighbors() {
        List<Integer> active = new ArrayList<>();
        for (int n : Config.getNeighbors(data.id)) {
            boolean isDropped = false;
            for (int[] edge : Config.droppedEdges) {
                if ((edge[0] == data.id && edge[1] == n) || (edge[0] == n && edge[1] == data.id)) {
                    isDropped = true; break;
                }
            }
            if (!isDropped) active.add(n);
        }
        return active;
    }

    // Главный метод
    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1098");
        p.setParameter(Profile.GUI, "true"); // Включение GUI

        AgentContainer container = rt.createMainContainer(p);
        // Создание и запуск всех агентов
        for (int i = 1; i <= Config.TOTAL_AGENTS; i++) {
            container.createNewAgent("Агент" + i, main.class.getName(), new Object[]{}).start();
        }
    }
}
