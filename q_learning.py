import random
import numpy as np

class SimpleGridEnv:
    """Entorno de cuadrícula (GridWorld) simple para Q-Learning."""
    def __init__(self, width=4, height=4):
        self.width = width
        self.height = height
        self.start = (0, 0)
        self.goal = (width - 1, height - 1)
        self.trap = (1, 1)
        self.state = self.start
        self.actions = [0, 1, 2, 3] # 0: Arriba, 1: Abajo, 2: Izquierda, 3: Derecha

    def reset(self):
        self.state = self.start
        return self.state

    def step(self, action):
        x, y = self.state
        if action == 0: y = max(0, y - 1)
        elif action == 1: y = min(self.height - 1, y + 1)
        elif action == 2: x = max(0, x - 1)
        elif action == 3: x = min(self.width - 1, x + 1)
        
        self.state = (x, y)
        
        if self.state == self.goal:
            return self.state, 10.0, True
        elif self.state == self.trap:
            return self.state, -10.0, True
        else:
            return self.state, -0.1, False

class QLearningAgent:
    def __init__(self, n_actions, alpha=0.1, gamma=0.99, epsilon=1.0, epsilon_decay=0.995, min_epsilon=0.01):
        self.q_table = {}
        self.n_actions = n_actions
        self.alpha = alpha       # Tasa de aprendizaje
        self.gamma = gamma       # Factor de descuento
        self.epsilon = epsilon   # Exploración inicial
        self.epsilon_decay = epsilon_decay
        self.min_epsilon = min_epsilon

    def get_q(self, state, action):
        return self.q_table.get((state, action), 0.0)

    def choose_action(self, state):
        if random.uniform(0, 1) < self.epsilon:
            return random.randint(0, self.n_actions - 1)
        q_values = [self.get_q(state, a) for a in range(self.n_actions)]
        max_q = max(q_values)
        # Desempate aleatorio si hay múltiples acciones con el mismo Q máximo
        best_actions = [a for a, q in enumerate(q_values) if q == max_q]
        return random.choice(best_actions)

    def update(self, state, action, reward, next_state, done):
        current_q = self.get_q(state, action)
        if done:
            max_future_q = 0.0
        else:
            max_future_q = max([self.get_q(next_state, a) for a in range(self.n_actions)])
        
        # Fórmula de actualización Q-learning
        new_q = current_q + self.alpha * (reward + self.gamma * max_future_q - current_q)
        self.q_table[(state, action)] = new_q

    def decay_epsilon(self):
        self.epsilon = max(self.min_epsilon, self.epsilon * self.epsilon_decay)

# Entrenamiento de prueba
if __name__ == "__main__":
    env = SimpleGridEnv()
    agent = QLearningAgent(n_actions=4)
    episodes = 1000

    print("Entrenando agente Q-Learning...")
    for episode in range(episodes):
        state = env.reset()
        done = False
        while not done:
            action = agent.choose_action(state)
            next_state, reward, done = env.step(action)
            agent.update(state, action, reward, next_state, done)
            state = next_state
        agent.decay_epsilon()

    print("Entrenamiento finalizado. Probando política aprendida:")
    state = env.reset()
    done = False
    path = [state]
    while not done:
        action = agent.choose_action(state)
        # Forzar explotación greedy pura en la prueba
        q_values = [agent.get_q(state, a) for a in range(agent.n_actions)]
        action = q_values.index(max(q_values))
        state, reward, done = env.step(action)
        path.append(state)

    print(f"Camino seguido por la IA: {path}")
