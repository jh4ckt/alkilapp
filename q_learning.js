class SimpleGridEnv {
    constructor(width = 4, height = 4) {
        this.width = width;
        this.height = height;
        this.start = [0, 0];
        this.goal = [width - 1, height - 1];
        this.trap = [1, 1];
        this.state = [...this.start];
        this.steps = 0;
        this.maxSteps = 30;
    }

    reset() {
        this.state = [...this.start];
        this.steps = 0;
        return [...this.state];
    }

    step(action) {
        this.steps++;
        let [x, y] = this.state;
        if (action === 0) y = Math.max(0, y - 1);       // Arriba
        else if (action === 1) y = Math.min(this.height - 1, y + 1); // Abajo
        else if (action === 2) x = Math.max(0, x - 1);       // Izquierda
        else if (action === 3) x = Math.min(this.width - 1, x + 1);  // Derecha

        this.state = [x, y];

        if (x === this.goal[0] && y === this.goal[1]) {
            return { nextState: [...this.state], reward: 10.0, done: true };
        } else if (x === this.trap[0] && y === this.trap[1]) {
            return { nextState: [...this.state], reward: -10.0, done: true };
        } else if (this.steps >= this.maxSteps) {
            return { nextState: [...this.state], reward: -1.0, done: true };
        } else {
            return { nextState: [...this.state], reward: -0.01, done: false };
        }
    }
}

class QLearningAgent {
    constructor(nActions = 4, alpha = 0.2, gamma = 0.9, epsilon = 1.0, epsilonDecay = 0.99) {
        this.qTable = new Map();
        this.nActions = nActions;
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.epsilonDecay = epsilonDecay;
        this.minEpsilon = 0.05;
    }

    getKey(state, action) {
        return `${state[0]},${state[1]}_${action}`;
    }

    getQ(state, action) {
        return this.qTable.get(this.getKey(state, action)) || 0.0;
    }

    chooseAction(state) {
        if (Math.random() < this.epsilon) {
            return Math.floor(Math.random() * this.nActions);
        }
        let qValues = [];
        for (let a = 0; a < this.nActions; a++) {
            qValues.push(this.getQ(state, a));
        }
        let maxQ = Math.max(...qValues);
        let bestActions = qValues.map((q, a) => q === maxQ ? a : -1).filter(a => a !== -1);
        return bestActions[Math.floor(Math.random() * bestActions.length)];
    }

    update(state, action, reward, nextState, done) {
        let currentQ = this.getQ(state, action);
        let maxFutureQ = 0.0;
        if (!done) {
            let futureQs = [];
            for (let a = 0; a < this.nActions; a++) {
                futureQs.push(this.getQ(nextState, a));
            }
            maxFutureQ = Math.max(...futureQs);
        }
        let newQ = currentQ + this.alpha * (reward + this.gamma * maxFutureQ - currentQ);
        this.qTable.set(this.getKey(state, action), newQ);
    }

    decayEpsilon() {
        this.epsilon = Math.max(this.minEpsilon, this.epsilon * this.epsilonDecay);
    }
}

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

function drawGrid(state, env, stepNum) {
    console.log(`\x1B[2J\x1B[H`); // Limpiar pantalla en consola de manera limpia
    console.log(`=== PASO ${stepNum} ===`);
    for (let y = 0; y < env.height; y++) {
        let row = "";
        for (let x = 0; x < env.width; x++) {
            if (x === state[0] && y === state[1]) {
                row += " 🤖 "; // IA
            } else if (x === env.goal[0] && y === env.goal[1]) {
                row += " 🏆 "; // Meta / Objetivo
            } else if (x === env.trap[0] && y === env.trap[1]) {
                row += " 💥 "; // Trampa
            } else {
                row += " ⬜ "; // Vacío
            }
        }
        console.log(row);
    }
    console.log("================\n");
}

async function run() {
    const env = new SimpleGridEnv();
    const agent = new QLearningAgent(4);
    const episodes = 500;

    console.log("Entrenando IA...");
    for (let ep = 0; ep < episodes; ep++) {
        let state = env.reset();
        let done = false;
        while (!done) {
            let action = agent.chooseAction(state);
            let res = env.step(action);
            agent.update(state, action, res.reward, res.nextState, res.done);
            state = res.nextState;
            done = res.done;
        }
        agent.decayEpsilon();
    }

    console.log("¡Entrenamiento completado!");
    await sleep(1000);

    let state = env.reset();
    let done = false;
    let stepNum = 0;

    drawGrid(state, env, stepNum);
    await sleep(800);

    while (!done && stepNum < 20) {
        stepNum++;
        let qValues = [];
        for (let a = 0; a < agent.nActions; a++) {
            qValues.push(agent.getQ(state, a));
        }
        let maxQ = Math.max(...qValues);
        let bestAction = qValues.indexOf(maxQ);

        let res = env.step(bestAction);
        state = res.nextState;
        done = res.done;

        drawGrid(state, env, stepNum);
        await sleep(800);
    }

    if (state[0] === env.goal[0] && state[1] === env.goal[1]) {
        console.log("¡La IA llegó a la meta! 🏆🎉");
    } else if (state[0] === env.trap[0] && state[1] === env.trap[1]) {
        console.log("¡La IA explotó en la trampa! 💥😭");
    } else {
        console.log("Se agotaron los pasos.");
    }
}

run();
