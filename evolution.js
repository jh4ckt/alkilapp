class NeuralNetwork {
    constructor(inputNodes, hiddenNodes, outputNodes) {
        this.inputNodes = inputNodes;
        this.hiddenNodes = hiddenNodes;
        this.outputNodes = outputNodes;
        this.weightsIH = this.randomMatrix(this.hiddenNodes, this.inputNodes);
        this.weightsHO = this.randomMatrix(this.outputNodes, this.hiddenNodes);
        this.biasH = this.randomMatrix(this.hiddenNodes, 1);
        this.biasO = this.randomMatrix(this.outputNodes, 1);
    }
    sigmoid(x) { return 1 / (1 + Math.exp(-x)); }
    predict(inputArray) {
        let inputs = this.arrayToMatrix(inputArray);
        let hidden = this.matrixAdd(this.matrixMultiply(this.weightsIH, inputs), this.biasH).map(r => r.map(this.sigmoid));
        let output = this.matrixAdd(this.matrixMultiply(this.weightsHO, hidden), this.biasO).map(r => r.map(this.sigmoid));
        return this.matrixToArray(output);
    }
    mutate(rate) {
        this.weightsIH = this.mutateMatrix(this.weightsIH, rate);
        this.weightsHO = this.mutateMatrix(this.weightsHO, rate);
    }
    randomMatrix(r, c) { return Array.from({ length: r }, () => Array.from({ length: c }, () => Math.random() * 2 - 1)); }
    mutateMatrix(m, rate) { return m.map(row => row.map(val => Math.random() < rate ? val + (Math.random() * 0.5 - 0.25) : val)); }
    matrixMultiply(a, b) {
        let res = Array.from({ length: a.length }, () => Array(b[0].length).fill(0));
        for (let i = 0; i < a.length; i++) for (let j = 0; j < b[0].length; j++) for (let k = 0; k < a[0].length; k++) res[i][j] += a[i][k] * b[k][j];
        return res;
    }
    matrixAdd(a, b) { return a.map((row, i) => row.map((val, j) => val + b[i][j])); }
    arrayToMatrix(arr) { return arr.map(x => [x]); }
    matrixToArray(mat) { return mat.map(row => row[0]); }
}

class Agent {
    constructor() {
        this.brain = new NeuralNetwork(2, 4, 1);
        this.fitness = 0;
    }
    // "Comportamiento": Qué tan cerca está la salida de 1.0 (Meta: salida 1.0 para entrada [1,1])
    calculateFitness(targetInput) {
        let output = this.brain.predict(targetInput)[0];
        this.fitness = 1 - Math.abs(1.0 - output); // Objetivo: output = 1
    }
}

// --- SIMULACIÓN EVOLUTIVA ---
const populationSize = 50;
let population = Array.from({ length: populationSize }, () => new Agent());
const generations = 10;

console.log("Iniciando evolución artificial...");
for (let gen = 0; gen < generations; gen++) {
    // 1. Evaluar
    population.forEach(agent => agent.calculateFitness([1, 1]));
    
    // 2. Ordenar por éxito (Selección)
    population.sort((a, b) => b.fitness - a.fitness);
    
    console.log(`Generación ${gen + 1} | Mejor Fitness: ${population[0].fitness.toFixed(4)}`);
    
    // 3. Reproducción (los mejores sobreviven y mutan)
    let nextGen = population.slice(0, 10); // Los 10 mejores pasan
    while (nextGen.length < populationSize) {
        let parent = nextGen[Math.floor(Math.random() * nextGen.length)];
        let child = new Agent();
        child.brain.weightsIH = parent.brain.weightsIH.map(r => [...r]);
        child.brain.weightsHO = parent.brain.weightsHO.map(r => [...r]);
        child.brain.mutate(0.1); // 10% tasa de mutación
        nextGen.push(child);
    }
    population = nextGen;
}
console.log("¡Evolución completada!");
