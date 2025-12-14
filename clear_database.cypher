// 清理所有节点和关系
MATCH (n)
DETACH DELETE n;

// 验证清理结果
MATCH (n)
RETURN count(n) as nodeCount;

